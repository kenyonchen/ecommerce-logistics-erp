package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ozon 订单拉取适配器：{@code POST /v3/posting/fbs/list} 取单号，再逐单
 * {@code POST /v2/posting/fbs/get} 取明细。
 * <p>
 * 两步是 §8 硬规定的（docs/spec/platform-api-reference.md §8「列表不返回明细，须逐单再调
 * /v2/posting/fbs/get」），不是可选优化：只调列表的话每单的收件地址、商品行、金额全都拿不到，
 * 导入侧的必填校验会整批失败。逐单回取的量由两个夹子兜住——单页条数 = 请求的 {@code limit}
 * （夹到 §8 的 1000 上限），总行数不超过 {@code request.limit()}，因此单次同步最多发出
 * {@code ceil(limit/limit)} 次列表 + {@code limit} 次明细，不会随平台订单总量失控。
 * <p>
 * 另外两条 §8 的坑在本类里都有对应动作：
 * <ul>
 *   <li>{@code filter.since}/{@code filter.until} <b>必须带时区偏移</b>，裸 {@code LocalDateTime} 会被平台判
 *       {@code bad time}；这里统一用 {@code PlatformJson.toOffsetQueryTimestamp(…, ZoneOffset.UTC)}。</li>
 *   <li>只接 FBS（卖家自发货）。<b>FBO（{@code /v3/posting/fbo/list}）不在本轮范围内</b>，
 *       §8 明确它没有明细级取单需求且平台侧由 Ozon 仓发货，请勿「顺手加上」而把两种发货模式混进同一批行。</li>
 * </ul>
 * 指定了平台单号时直接走 {@code /v2/posting/fbs/get} 一单一次调用，而不是按时间窗拉列表再筛——
 * 该端点本身就是 §8 记录的按 {@code posting_number} 精确取单接口。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§8 核实到的是端点与信封（{@code {result:{posts[],has_more},has_more}}），
 * 不是 posting 对象的结构。因此除单号与状态外，金额/币种/收件人/明细一律按候选键探测，探测不到就留空
 * 而不是断言某种形状；这些待实测项归入 ISS-065。输出的行键与 {@code AmazonOrderFetcher}/{@code ShopifyOrderFetcher}
 * 同一套 {@code OrderImportTargetField} 中立别名。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class OzonOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * 没有时间界时的回溯窗口（天），沿用 Amazon/eBay 适配器的同名口径
     */
    private static final int DEFAULT_BACKFILL_DAYS = 30;

    /**
     * §8 核实了列表体里有 {@code dir}/{@code sort} 两个键，但没核实 {@code sort} 的可选字段名；
     * 这里用 posting 对象里最确定的时间字段 {@code created_at} 配 {@code asc}，让 {@code offset} 翻页有稳定次序。
     */
    static final String SORT_FIELD = "created_at";
    static final String DIRECTION = "asc";

    /**
     * 单号数组在信封里的候选键：§8 核实的是 {@code result.posts}，另两种是兜底探测
     */
    private static final String[] POSTS_KEYS = {"posts", "postings", "result"};

    private final OzonSellerApi api;

    public OzonOrderFetcher(OzonSellerApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.OZON.name();
    }

    /**
     * Ozon 必须要 Client-Id/Api-Key，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Ozon 订单适配器需要凭据上下文(apiKey=Client-Id、apiSecret=Api-Key)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        String wantedPosting = trimToNull(request.orderNumber());
        if (wantedPosting != null) {
            Map<String, Object> posting = detail(credential, wantedPosting);
            return posting.isEmpty() ? List.of() : List.of(toRow(posting, Map.of()));
        }

        int pageLimit = OzonSellerApi.clampListLimit(request.limit());
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenPostings = new HashSet<>();
        int offset = 0;
        int pages = 0;
        int detailCalls = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            String body = api.postJson(credential, OzonSellerApi.ORDERS_LIST_PATH,
                    listBody(request, pageLimit, offset)).getBody();
            Map<String, Object> root = rootOf(body);
            List<Map<String, Object>> summaries = postsIn(body);
            for (Map<String, Object> summary : summaries) {
                String postingNumber = PlatformJson.text(summary, "posting_number");
                if (postingNumber == null || !seenPostings.add(postingNumber)) {
                    // 没有单号就没法逐单回取，也不能把「同一单」重复出行（导入侧按幂等键会互相覆盖）
                    continue;
                }
                if (rows.size() >= totalLimit) {
                    break;
                }
                Map<String, Object> posting = detail(credential, postingNumber);
                detailCalls++;
                rows.add(toRow(posting.isEmpty() ? summary : posting, summary));
            }
            pages++;
            offset += pageLimit;
            hasMore = !summaries.isEmpty() && Boolean.TRUE.equals(hasMore(root));
        }
        if (hasMore) {
            log.warn("[Ozon] 订单拉取达到翻页上限 {}，尚有后续页未取回 platformOrders={}", api.maxPages(), rows.size());
        }
        log.info("[Ozon] 订单拉取完成 pages={} rows={} detailCalls={}", pages, rows.size(), detailCalls);
        return rows;
    }

    /**
     * 把 posting 对象映射为导入侧的中立行；{@code summary} 是列表页里的同单条目，只用于明细接口没给某字段时兜底。
     * 源键全部是候选探测（§8 未核实 posting 对象结构），探不到就留空。
     */
    Map<String, Object> toRow(Map<String, Object> posting, Map<String, Object> summary) {
        Map<String, Object> row = PlatformJson.linkedRow();
        Map<String, Object> customer = PlatformJson.nested(posting, "customer");
        Map<String, Object> address = PlatformJson.nested(posting, "address");
        Map<String, Object> financialData = PlatformJson.nested(posting, "financial_data");
        Map<String, Object> pricing = asObject(PlatformJson.path(posting, "pricing"));
        List<Map<String, Object>> products = products(posting, financialData);

        row.put("orderId", firstNonBlank(PlatformJson.text(posting, "posting_number"),
                PlatformJson.text(summary, "posting_number")));
        row.put("customerName", firstNonBlank(PlatformJson.text(customer, "name"),
                PlatformJson.text(customer, "full_name"),
                PlatformJson.text(posting, "customer_name")));
        row.put("email", firstNonBlank(PlatformJson.text(customer, "email"),
                PlatformJson.text(posting, "email")));
        row.put("orderStatus", bridgeStatus(firstNonBlank(PlatformJson.text(posting, "status"),
                PlatformJson.text(summary, "status"))));
        // created_at 是下单时间；平台没给时退到 in_process_at / shipment_date（三者都是 §8 点名的 *At 字段）
        row.put("orderTime", PlatformJson.toImportDateTime(firstNonBlank(readTime(posting, "created_at"),
                readTime(posting, "in_process_at"), readTime(posting, "shipment_date"),
                readTime(summary, "created_at"))));
        row.put("currency", firstNonBlank(PlatformJson.text(posting, "currency_code"),
                currencyCode(pricing), firstValue(products, "currency_code")));
        row.put("totalAmount", firstNonBlank(PlatformJson.text(posting, "order_amount"),
                amountOf(pricing, "total"), amountOf(pricing, "price"),
                firstValue(products, "payer_total_amount")));
        row.put("paymentMethod", firstNonBlank(PlatformJson.text(posting, "payment_method"),
                PlatformJson.text(financialData, "payment_method"),
                PlatformJson.text(posting, "payment_type")));
        row.put("paymentStatus", firstNonBlank(PlatformJson.text(posting, "payment_status"),
                PlatformJson.text(financialData, "payment_status")));
        row.put("recipientName", firstNonBlank(PlatformJson.text(address, "name"),
                PlatformJson.text(address, "full_name"),
                PlatformJson.text(PlatformJson.nested(address, "contact"), "name"),
                PlatformJson.text(customer, "name")));
        row.put("phoneNumber", firstNonBlank(PlatformJson.text(address, "phone"),
                PlatformJson.text(PlatformJson.nested(address, "contact"), "phone"),
                PlatformJson.text(customer, "phone")));
        row.put("country", firstNonBlank(PlatformJson.text(address, "country"),
                PlatformJson.text(address, "country_code")));
        row.put("province", firstNonBlank(PlatformJson.text(address, "region"),
                PlatformJson.text(address, "province"),
                PlatformJson.text(address, "state")));
        row.put("city", PlatformJson.text(address, "city"));
        row.put("addressLine1", firstNonBlank(PlatformJson.text(address, "address"),
                PlatformJson.text(address, "address_line1"),
                PlatformJson.text(address, "address_block"),
                PlatformJson.text(address, "street")));
        row.put("zipCode", firstNonBlank(PlatformJson.text(address, "zip_code"),
                PlatformJson.text(address, "postal_code")));
        // 明细数组原样透传：元素结构未核实，实测后在此处一次性对齐
        row.put("lineItems", products);
        return row;
    }

    /**
     * 状态桥接：Ozon 的 FBS {@code status} 是平台自己的枚举，ERP 侧只认 {@code OrderStatus} 的六个码。
     * 下表来自本轮交付约定的映射口径（§8 记录了状态字段存在与取值形态，未逐个枚举核实）：
     * <pre>
     *   awaiting_packaging / awaiting_deliver -&gt; processing  已收款待发货
     *   delivering / driver_pickup            -&gt; shipped     已在途
     *   delivered                              -&gt; completed   已签收
     *   cancelled                              -&gt; cancelled
     *   其他（awaiting_registration / acceptance_in_progress / arbitrating …）-&gt; 原样透出
     * </pre>
     * 认不出的一律原样透出而不是硬塞进 {@code pending}：导入侧 {@code toOrderStatus} 会兜底成待处理，
     * 但保留原文才能让人看见平台侧出现了新的状态值。比较前把下划线/连字符归一为空格，
     * 故必须按完整取值匹配——{@code delivered} 与 {@code delivering} 字面互为前缀，用「包含」会判错。
     */
    static String bridgeStatus(String ozonStatus) {
        if (ozonStatus == null || ozonStatus.isBlank()) {
            return null;
        }
        String value = ozonStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ').replace('-', ' ');
        return switch (normalized) {
            case "awaiting packaging", "awaiting deliver" -> "processing";
            case "delivering", "driver pickup" -> "shipped";
            case "delivered" -> "completed";
            case "cancelled" -> "cancelled";
            default -> value;
        };
    }

    /**
     * 列表请求体：{@code {dir, sort, filter:{since, until, limit, offset, status?}, with:{…}}}（§8 原样）。
     * {@code filter.status} 是 §8 标注的可选键，同步不按平台状态收窄，故不发；
     * {@code with} 的子键（哪些附加块一并返回）§8 未核实，这里发空对象——明细本来就逐单回取，不靠它。
     */
    private static Map<String, Object> listBody(PlatformOrderFetchRequest request, int limit, int offset) {
        LocalDateTime until = request.updatedTo() == null
                ? LocalDateTime.now(ZoneOffset.UTC) : request.updatedTo();
        LocalDateTime since = request.updatedFrom() == null
                ? until.minusDays(DEFAULT_BACKFILL_DAYS) : request.updatedFrom();
        Map<String, Object> filter = PlatformJson.linkedRow();
        filter.put("since", PlatformJson.toOffsetQueryTimestamp(since, ZoneOffset.UTC));
        filter.put("until", PlatformJson.toOffsetQueryTimestamp(until, ZoneOffset.UTC));
        filter.put("limit", limit);
        filter.put("offset", offset);
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("dir", DIRECTION);
        body.put("sort", SORT_FIELD);
        body.put("filter", filter);
        body.put("with", PlatformJson.linkedRow());
        return body;
    }

    /**
     * §8：明细接口体是 {@code {posting_number, language?}}；{@code language} 的可选取值集合未核实，
     * 不发该键即用平台默认语言，比臆造一个 {@code "EN"} 安全。
     */
    private Map<String, Object> detail(PlatformCredential credential, String postingNumber) {
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("posting_number", postingNumber);
        String response = api.postJson(credential, OzonSellerApi.ORDER_DETAIL_PATH, body).getBody();
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        return PlatformJson.wrappedObject(response, "result");
    }

    /**
     * 解出列表响应里的单号条目（连通性测试也复用）。
     */
    static List<Map<String, Object>> postsIn(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return List.of();
        }
        for (String key : POSTS_KEYS) {
            List<Map<String, Object>> posts = PlatformJson.collection(jsonBody, key);
            if (!posts.isEmpty()) {
                return posts;
            }
        }
        return List.of();
    }

    /**
     * {@code has_more} 在 §8 的信封里出现两次（{@code result.has_more} 与根上的 {@code has_more}），
     * 两处都探；平台把它写成字符串 {@code "true"} 时同样认。
     */
    private static Boolean hasMore(Map<String, Object> root) {
        Object value = PlatformJson.path(root, "result", "has_more");
        if (value == null) {
            value = root.get("has_more");
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    /**
     * §8 点名 posting 里有 {@code financial_data.products[]} 与 {@code pricing} 两处商品级数据；
     * 行项目取先能探到的那一处，取不到就是空数组（由导入侧的必填校验去报这一行）。
     */
    private static List<Map<String, Object>> products(Map<String, Object> posting, Map<String, Object> financialData) {
        List<Map<String, Object>> products = PlatformJson.objects(PlatformJson.path(posting, "products"));
        if (!products.isEmpty()) {
            return products;
        }
        return PlatformJson.objects(PlatformJson.path(financialData, "products"));
    }

    /**
     * {@code pricing} 可能是对象，也可能是 §8 之外常见的 JSON 字符串形态；两种都试，解析不出来就当作没有。
     */
    private static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return asMap(map);
        }
        if (value instanceof String text && text.trim().startsWith("{")) {
            try {
                return PlatformJson.object(text);
            } catch (BusinessException e) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static String amountOf(Map<String, Object> pricing, String key) {
        Object node = pricing.get(key);
        if (node instanceof Map<?, ?> map) {
            return PlatformJson.text(asMap(map), "amount");
        }
        return scalarText(node);
    }

    private static String currencyCode(Map<String, Object> pricing) {
        String direct = PlatformJson.text(pricing, "currency_code");
        if (direct != null) {
            return direct;
        }
        Object node = pricing.get("total");
        if (node instanceof Map<?, ?> map) {
            return PlatformJson.text(asMap(map), "currency_code");
        }
        return null;
    }

    private static String firstValue(List<Map<String, Object>> rows, String key) {
        for (Map<String, Object> row : rows) {
            String value = PlatformJson.text(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 时间只接受字符串形态：§8 说所有 {@code *At} 字段是带偏移的 RFC3339 字符串，
     * 万一是对象就别把 {@code toString} 当时间丢给导入侧。
     */
    private static String readTime(Map<String, Object> node, String key) {
        return scalarText(PlatformJson.path(node, key));
    }

    private static String scalarText(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
