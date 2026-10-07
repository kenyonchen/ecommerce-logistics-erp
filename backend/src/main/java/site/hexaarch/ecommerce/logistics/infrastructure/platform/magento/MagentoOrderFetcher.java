package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Magento 订单拉取适配器：{@code GET /rest/<store>/V1/orders}（docs/spec/platform-api-reference.md §9）。
 * <p>
 * 查询形状全部按 §9 的 {@code searchCriteria} 数组语法发：{@code searchCriteria[pageSize]} 与
 * {@code searchCriteria[currentPage]}（页码从 1 起）驱动翻页，时间界走
 * {@code searchCriteria[filter_groups][0][filters][0][field|value|condition_type]}，
 * 且 {@code field}/{@code value}/{@code condition_type} <b>三者各出现一次</b>（§9：少一个条件会被静默丢弃）。
 * 上下界分放两个 {@code filter_groups}，因为 §9 核实的是<b>组间 AND、组内 filters 之间 OR</b>——
 * 两个条件写进同一组就从「区间」变成「或」，增量窗口直接失效。
 * 没有任何时间界时<b>不发</b>时间过滤：§9 不要求它必填，所以也不像 Amazon 那样编一个 30 天回溯窗口。
 * <p>
 * 翻页由响应外层 {@code total_count}（§9 的信封 {@code {items[], search_criteria, total_count}}）驱动：
 * 已消费行数达到 {@code total_count}、本页没有条目、或整页都是见过的订单（平台没跟着 {@code currentPage} 前进）
 * 三种情况都收手，另有 {@code platform.magento.cursor.max-pages} 兜底。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§9 核实的是查询语法与信封，<b>不是</b>订单对象的键集合。
 * 因此除订单号与状态外，收件人/地址/金额/明细一律按候选键探测，探不到就留空而不是断言某种形状，
 * 待实测项归入 ISS-065。已知的两处风险：
 * <ul>
 *   <li>{@code orderId} 取 {@code entity_id}（数字主键）而不是 {@code increment_id}（前台展示的 {@code 000000063}）：
 *       §9 的发货端点要把订单号拼进 {@code /V1/order/<orderId>/ship} 路径，那里平台认的是数字主键。
 *       取 {@code increment_id} 会让导入回来的单号在回写时全打不通。</li>
 *   <li>{@code created_at} 一类时间字段可能按 MySQL 的 {@code yyyy-MM-dd HH:mm:ss}（无 {@code T}、无偏移）返回，
 *       而 {@code PlatformJson.toImportDateTime} 只认 ISO 形态，故此处先把空格换成 {@code T}。
 *       真实偏移口径需实测校正。</li>
 * </ul>
 * 输出的行键与 {@code ShopifyOrderFetcher}/{@code AmazonOrderFetcher} 同一套
 * {@code OrderImportTargetField} 中立别名，导入侧的字段映射与校验可直接复用。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MagentoOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * {@code currentPage} 的起始页（§9 的 {@code searchCriteria[currentPage]} 从 1 起）
     */
    private static final int FIRST_PAGE = 1;

    /**
     * §9：增量拉取的过滤字段。有更新界时按 {@code updated_at}，只有一个上界时按 {@code created_at}
     */
    static final String FIELD_UPDATED_AT = "updated_at";
    static final String FIELD_CREATED_AT = "created_at";

    /**
     * 订单的数字主键字段名：既是列表行里 {@code orderId} 的取值来源，也是 {@code condition_type=eq}
     * 精确取单时的过滤字段（发货回写要用，见 {@code MagentoFulfillmentPusher#resolveItems}）。
     */
    static final String FIELD_ENTITY_ID = "entity_id";

    /**
     * §9 的 {@code condition_type} 全集（eq/gt/in/like/nlike/null/from/to/finset）中的区间两端
     */
    private static final String CONDITION_FROM = "from";
    private static final String CONDITION_TO = "to";

    /**
     * §9 的信封里条目数组与总条数的键名。商品列表是同一个信封，故两处共用。
     */
    private static final String ITEMS_KEY = "items";
    static final String TOTAL_COUNT_KEY = "total_count";

    /**
     * 过滤值的日期时间写法。§9 没给 {@code from}/{@code to} 的取值格式，这里按 MySQL DATETIME 列的字面形态发，
     * 与 {@code sales_order.updated_at} 的存储格式一致（待实测校正，ISS-065）。
     */
    private static final DateTimeFormatter QUERY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MagentoRestApi api;

    public MagentoOrderFetcher(MagentoRestApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MAGENTO.name();
    }

    /**
     * Magento 必须要令牌与自建 base URL，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Magento 订单适配器需要凭据上下文(storeUrl 与 accessToken 或 apiKey/apiSecret)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        MagentoAdminToken token = api.tokenFor(credential);
        int pageSize = MagentoRestApi.pageSize(request.limit());
        String wantedOrderId = trimToNull(request.orderNumber());
        // §9 的列表接口没有按订单号查询的参数，只能取回后筛，与 Shopify/Amazon 同一口径
        Map<String, String> timeFilters = new LinkedHashMap<>();
        putTimeFilters(timeFilters, request);

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        int firstPage = request.page() == null || request.page() < FIRST_PAGE ? FIRST_PAGE : request.page();
        int page = firstPage;
        long consumed = 0;
        boolean hasMore = true;
        while (hasMore && page - firstPage < api.maxPages() && rows.size() < totalLimit) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put(MagentoRestApi.PAGE_SIZE_KEY, String.valueOf(pageSize));
            query.put(MagentoRestApi.CURRENT_PAGE_KEY, String.valueOf(page));
            query.putAll(timeFilters);
            String body = api.get(api.ordersUri(credential, query), token).getBody();
            List<Map<String, Object>> items = itemsIn(body);
            if (items.isEmpty()) {
                hasMore = false;
                break;
            }
            consumed += items.size();
            Integer totalCount = PlatformJson.integer(rootOf(body), TOTAL_COUNT_KEY);
            int unseen = 0;
            for (Map<String, Object> order : items) {
                Map<String, Object> row = toRow(order);
                // 平台跨页重复返回同一单不能重复出行，否则导入侧按幂等键会互相覆盖
                if (!markSeen(seenOrderIds, row.get("orderId"))) {
                    continue;
                }
                unseen++;
                if (wantedOrderId == null || wantedOrderId.equals(row.get("orderId"))) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            if (unseen == 0) {
                // 整页都是见过的单，说明 currentPage 没让平台前进，继续翻只是在页数上限上空转
                hasMore = false;
                break;
            }
            page++;
            hasMore = totalCount == null || consumed < totalCount;
        }
        if (hasMore && rows.size() < totalLimit) {
            log.warn("[Magento] 订单拉取达到翻页上限 {}，尚有后续页未取回 platformOrders={}", api.maxPages(), rows.size());
        }
        log.info("[Magento] 订单拉取完成 pages={} rows={} storeView={}",
                page - firstPage, rows.size(), api.storeView(credential));
        return rows;
    }

    /**
     * 把 Magento 订单对象映射为导入侧的中立行。地址取收货地址、没有收货地址时退到账单地址
     * （§9 记录列表响应带 {@code extension_attributes}/{@code billing_address}，但订单对象的键集合本身未核实，
     * 故全部按候选键探测）。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = PlatformJson.linkedRow();
        Map<String, Object> shipping = shippingAddress(order);
        Map<String, Object> billing = PlatformJson.nested(order, "billing_address");
        Map<String, Object> payment = paymentNode(order);

        row.put("orderId", firstIdText(order, FIELD_ENTITY_ID, "id"));
        row.put("customerName", firstNonBlank(nameOf(shipping), nameOf(billing),
                combine(PlatformJson.text(order, "customer_firstname"), PlatformJson.text(order, "customer_lastname")),
                PlatformJson.text(order, "customer_name")));
        row.put("email", firstNonBlank(PlatformJson.text(order, "customer_email"),
                PlatformJson.text(billing, "email")));
        // §9：列表里的 status 是「标签字符串」（店铺可自定义文案），所以要过一层桥接而不是当状态码用
        row.put("orderStatus", bridgeStatus(firstNonBlank(PlatformJson.text(order, "status"),
                PlatformJson.text(order, "state"))));
        row.put("orderTime", toImportDateTime(firstNonBlank(PlatformJson.text(order, "created_at"),
                PlatformJson.text(order, "updated_at"))));
        row.put("currency", firstNonBlank(PlatformJson.text(order, "order_currency_code"),
                PlatformJson.text(order, "base_currency_code")));
        // 金额必须走 text 而不是 idText：grand_total 是 JSON 数字，idText 会按 longValue 截掉小数
        row.put("totalAmount", firstNonBlank(PlatformJson.text(order, "grand_total"),
                PlatformJson.text(order, "base_grand_total")));
        row.put("paymentMethod", firstNonBlank(PlatformJson.text(payment, "method"),
                PlatformJson.text(order, "payment_method")));
        row.put("paymentStatus", firstNonBlank(PlatformJson.text(payment, "status"),
                PlatformJson.text(order, "payment_status")));
        row.put("recipientName", firstNonBlank(nameOf(shipping), nameOf(billing)));
        row.put("phoneNumber", firstNonBlank(PlatformJson.text(shipping, "telephone"),
                PlatformJson.text(billing, "telephone")));
        row.put("country", firstNonBlank(PlatformJson.text(shipping, "country_id"),
                PlatformJson.text(billing, "country_id")));
        row.put("province", firstNonBlank(PlatformJson.text(shipping, "region"),
                PlatformJson.text(billing, "region")));
        row.put("city", firstNonBlank(PlatformJson.text(shipping, "city"),
                PlatformJson.text(billing, "city")));
        row.put("addressLine1", firstNonBlank(scalarOrJoined(shipping, "street"),
                scalarOrJoined(billing, "street")));
        row.put("zipCode", firstNonBlank(PlatformJson.text(shipping, "postcode"),
                PlatformJson.text(billing, "postcode")));
        // 明细数组原样透传：§9 只核实了列表带 items[]，元素键名未核实，实测后在此处一次性对齐
        row.put("lineItems", lineItems(order));
        return row;
    }

    /**
     * 状态桥接：§9 明确列表里的 {@code status} 是标签字符串（且店铺可自定义文案），ERP 侧只认
     * {@code OrderStatus} 的六个码，所以要显式建表。下表左列是 Magento 的订单 {@code state} 全集
     * （new/pending/pending_payment/payment_review/holded/processing/complete/closed/canceled）
     * 及其默认标签文案：
     * <pre>
     *   canceled, cancelled                          -&gt; cancelled
     *   complete, closed                             -&gt; completed   已发货并开票/结账完成
     *   processing                                   -&gt; processing  已收款待发货
     *   （自定义标签含 ship 字样时）                  -&gt; shipped
     *   new, pending, pending_payment, payment_review, holded -&gt; pending
     *   其他                                          -&gt; 原样透出
     * </pre>
     * 判定顺序不可随意调换：{@code canceled} 最先判（唯一带「终止」语义又可能被别的词包含的），
     * {@code processing} 在 {@code ship} 之前、{@code ship} 又在 {@code pend} 之前，
     * 这样自定义文案「Pending Shipment」按在途归类而不是退回待处理。认不出的一律原样透出而不是硬塞进
     * {@code pending}：导入侧会兜底成待处理，但保留原文才能让人看见平台侧出现了新的文案。
     * 比较前把下划线/连字符归一为空格，因为 Magento 的 state 用下划线、标签文案用空格。
     */
    static String bridgeStatus(String magentoStatus) {
        if (magentoStatus == null || magentoStatus.isBlank()) {
            return null;
        }
        String value = magentoStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ').replace('-', ' ');
        if (normalized.contains("cancel")) {
            return "cancelled";
        }
        if (normalized.contains("complete") || normalized.contains("closed")) {
            return "completed";
        }
        if (normalized.contains("process")) {
            return "processing";
        }
        if (normalized.contains("ship")) {
            return "shipped";
        }
        if (normalized.contains("hold") || normalized.contains("review")
                || normalized.contains("pend") || normalized.contains("new")) {
            return "pending";
        }
        return value;
    }

    /**
     * §9 的搜索参数没有要求必须带时间界，所以两个界都没给时不发过滤器（而不是像 Amazon v0 那样
     * 必须编一个回溯窗口，否则平台拒请求）。字段选择：有更新界就是增量语义走 {@code updated_at}，
     * 只有上界时它是「创建时间上限」语义，走 {@code created_at}。
     */
    private static void putTimeFilters(Map<String, String> query, PlatformOrderFetchRequest request) {
        if (request.updatedFrom() == null && request.updatedTo() == null) {
            return;
        }
        String field = request.updatedFrom() != null ? FIELD_UPDATED_AT : FIELD_CREATED_AT;
        if (request.updatedFrom() != null) {
            MagentoRestApi.putFilter(query, 0, 0, field, QUERY_DATETIME.format(request.updatedFrom()), CONDITION_FROM);
        }
        if (request.updatedTo() != null) {
            MagentoRestApi.putFilter(query, request.updatedFrom() == null ? 0 : 1, 0,
                    field, QUERY_DATETIME.format(request.updatedTo()), CONDITION_TO);
        }
    }

    /**
     * 解出当前页的订单条目（连通性测试也复用）。§9 的信封键名是 {@code items}。
     */
    static List<Map<String, Object>> itemsIn(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return List.of();
        }
        return PlatformJson.collection(jsonBody, ITEMS_KEY);
    }

    /**
     * 订单明细：§9 记录列表响应带 {@code items[]}，键名按同层候选探测。
     */
    static List<Map<String, Object>> lineItems(Map<String, Object> order) {
        return PlatformJson.objects(PlatformJson.path(order, ITEMS_KEY));
    }

    /**
     * 收货地址的候选位置：部分部署直接给 {@code shipping_address}，API 标准形状是
     * {@code extension_attributes.shipping_assignments[].shipping.address}（§9 只点名了
     * {@code extension_attributes} 的存在，内部路径未核实，故两处都探）。
     */
    private static Map<String, Object> shippingAddress(Map<String, Object> order) {
        Map<String, Object> direct = PlatformJson.nested(order, "shipping_address");
        if (!direct.isEmpty()) {
            return direct;
        }
        for (Map<String, Object> assignment : PlatformJson.objects(
                PlatformJson.path(order, "extension_attributes", "shipping_assignments"))) {
            Map<String, Object> address = PlatformJson.nested(assignment, "shipping", "address");
            if (!address.isEmpty()) {
                return address;
            }
        }
        return Map.of();
    }

    /**
     * 支付方式所在的节点：{@code payment} 是订单的扩展属性，也可能是扁平的 {@code payment}。
     */
    private static Map<String, Object> paymentNode(Map<String, Object> order) {
        Map<String, Object> extended = PlatformJson.nested(order, "extension_attributes", "payment");
        return extended.isEmpty() ? PlatformJson.nested(order, "payment") : extended;
    }

    private static String nameOf(Map<String, Object> address) {
        return firstNonBlank(PlatformJson.text(address, "name"),
                combine(PlatformJson.text(address, "firstname"), PlatformJson.text(address, "lastname")));
    }

    /**
     * 地址行：Magento 的 {@code street} 是<b>数组</b>（多行街道），直接 {@code toString} 会变成
     * {@code [行1, 行2]}，所以按列表拼成逗号分隔的单行；平台给的是字符串时原样使用。
     */
    private static String scalarOrJoined(Map<String, Object> node, String key) {
        Object value = PlatformJson.path(node, key);
        if (!(value instanceof List<?> list)) {
            return PlatformJson.text(node, key);
        }
        StringBuilder joined = new StringBuilder();
        for (Object element : list) {
            String text = element == null ? null : String.valueOf(element).trim();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(text);
        }
        return joined.length() == 0 ? null : joined.toString();
    }

    /**
     * §9 未核实时间字段的返回格式：MySQL 的 {@code yyyy-MM-dd HH:mm:ss}（无 {@code T}、无偏移）也要能解，
     * 故先归一成 ISO 形态再交给 {@code PlatformJson}；它按 UTC 处理裸值，与导入侧的 {@code serverTimezone=UTC} 一致。
     */
    static String toImportDateTime(String rawTimestamp) {
        return PlatformJson.toImportDateTime(toIsoTimestamp(rawTimestamp));
    }

    /**
     * 同一份归一化逻辑的第二个出口：端口结果（如发货回写的 {@code fulfilledAt}）要的是类型化时间，
     * 导入行的时间槽位要的是字符串形态，两者不能各写一遍解析规则。
     */
    static LocalDateTime toUtcTimestamp(String rawTimestamp) {
        String iso = toIsoTimestamp(rawTimestamp);
        return iso == null ? null : PlatformJson.toUtcLocalDateTime(iso);
    }

    private static String toIsoTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            return null;
        }
        String value = rawTimestamp.trim();
        if (!value.contains("T") && value.contains(" ")) {
            value = value.replace(' ', 'T');
        }
        return value;
    }

    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    private static String firstIdText(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.idText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String combine(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    /**
     * 记录已出行过的平台订单号；没有订单号的行不能被当成「已经见过」，交给导入侧的必填校验去拦。
     */
    private static boolean markSeen(Set<String> seenOrderIds, Object orderId) {
        return orderId == null || seenOrderIds.add(String.valueOf(orderId));
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
