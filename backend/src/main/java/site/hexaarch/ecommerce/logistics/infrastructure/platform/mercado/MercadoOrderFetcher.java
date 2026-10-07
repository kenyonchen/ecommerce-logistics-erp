package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mercado Libre 订单拉取适配器：CBT 跨境口径的 {@code GET /marketplace/orders/search}
 * （docs/spec/platform-api-reference.md §7）。
 * <p>
 * 按 §7 核实到的形状调用：{@code seller.id}（= 集成的 {@code externalShopId}，必填）+ {@code limit}/{@code offset}
 * 翻页 + {@code last_updated.gte}/{@code last_updated.lt} 增量窗口（<b>值是带 UTC 偏移的 ISO-8601 字符串</b>，
 * 裸 {@code LocalDateTime} 会像 Ozon 那样被判非法）+ {@code sort}/{@code order}。
 * 翻页用「{@code offset += 单页条数}」推进，直到 {@code results} 为空或已取回 {@code paging.total} 条；
 * 两条停止条件都撞不上时由 {@code platform.mercado.cursor.max-pages} 兜底。
 * <p>
 * <b>§7 核实的是端点、参数与 {@code {results,paging}} 信封，核实到的状态枚举是「货件」的
 * （pending/ready_to_ship/shipped/delivered/cancelled），不是订单对象的字段</b>；
 * 也没有任何<b>逐单明细</b>端点被核实到（§7 的两个 shipment 端点都只能读，字段名同样未核实）。
 * 因此本类对订单里的每个字段都只<b>探测候选键</b>，探不到就留空而不是断言某种形状：
 * <ul>
 *   <li>探测的源键（全部待实测，ISS-065）：{@code id}、{@code order_items}/{@code items}、
 *       {@code total_amount}/{@code amount}、{@code currency_id}/{@code currency}、
 *       {@code paid_time}/{@code date_created}/{@code created_at}、{@code status}/{@code shipping_status}、
 *       {@code payment_method}、{@code payment_status}，以及嵌套对象 {@code buyer} 下的
 *       {@code nickname}/{@code name}/{@code email}；</li>
 *   <li>{@code seller} 不探测：中立行里没有卖家侧字段，而 {@code seller.id} 本来就要求由集成的
 *       {@code externalShopId} 提供（拿响应里的卖家去反查会让「配置错了卖家」变成静默拉错店）；</li>
 *   <li><b>收件地址（收件人/电话/国家/省州/城市/详址/邮编）不探测</b>：CBT 搜索行里没有任何候选地址对象，
 *       §7 给的地址来源是 {@code GET /marketplace/shipments/{id}}，而「订单行里指向货件的键叫什么」未核实，
 *       凭猜去串联两次调用会把一次可验证的读变成两处不可验证的猜——所以这些行键一律留空。</li>
 * </ul>
 * 后果写在 {@link MercadoApi} 的类注释里：明细或收件人为空的行会被导入侧必填校验整行拦下，
 * 等真实响应取证后在本类 {@code toRow} 一处补齐即可。
 * <p>
 * 输出行键与 {@code AmazonOrderFetcher}/{@code OzonOrderFetcher} 同一套 {@code OrderImportTargetField} 中立别名，
 * 平台原始键不外泄给导入侧。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MercadoOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * 两个时间界都没有时的回溯窗口（天），沿用 Amazon/Ozon 的同名口径：
     * 覆盖常规同步节奏的余量，又不至于去要平台订单保留期的全量数据而放大分页成本
     */
    private static final int DEFAULT_BACKFILL_DAYS = 30;

    /**
     * 与 {@code last_updated} 增量窗口同向，让「先取到的都是旧单」这一翻页假设成立；
     * {@code order} 的取值枚举未被 §7 核实（ISS-065），升序是这里唯一不自相矛盾的选项
     */
    static final String ORDER_DIRECTION = "asc";

    private static final String[] ORDER_ID_KEYS = {"id", "order_id"};
    private static final String[] CUSTOMER_NAME_KEYS = {"nickname", "name"};
    private static final String[] ORDER_STATUS_KEYS = {"status", "shipping_status"};
    private static final String[] ORDER_TIME_KEYS = {"paid_time", "date_created", "created_at"};
    private static final String[] CURRENCY_KEYS = {"currency_id", "currency"};
    private static final String[] TOTAL_AMOUNT_KEYS = {"total_amount", "amount"};
    private static final String[] LINE_ITEM_KEYS = {"order_items", "items"};

    /**
     * 有意留空的中立行键（导入侧别名存在，但 §7 没有核实到任何能提供这些值的订单搜索字段），
     * 显式出行是为了让「这一行缺收件信息」在导入校验里以字段名报出来，而不是键都不在
     */
    private static final String[] UNRESOLVED_ADDRESS_KEYS =
            {"recipientName", "phoneNumber", "country", "province", "city", "addressLine1", "zipCode"};

    private final MercadoApi api;

    public MercadoOrderFetcher(MercadoApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MERCADO_LIBRE.name();
    }

    /**
     * Mercado 必须要 Bearer 访问令牌与卖家 user_id，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Mercado Libre 订单适配器需要凭据上下文(accessToken、externalShopId=卖家 user_id)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        int pageLimit = MercadoApi.pageSize(request.limit());
        // §7 的搜索参数里没有按订单号过滤的键（CBT 只有 seller.id 与两组时间界），只能取回后筛，与 Amazon 同一口径
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        Map<String, String> query = new LinkedHashMap<>();
        query.put(MercadoApi.SELLER_ID_PARAM, api.sellerId(credential));
        putTimeWindow(query, request);
        query.put(MercadoApi.SORT_PARAM, MercadoApi.SORT_FIELD);
        query.put(MercadoApi.ORDER_PARAM, ORDER_DIRECTION);

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        int offset = 0;
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            Map<String, String> pageQuery = new LinkedHashMap<>(query);
            pageQuery.put(MercadoApi.LIMIT_PARAM, String.valueOf(pageLimit));
            pageQuery.put(MercadoApi.OFFSET_PARAM, String.valueOf(offset));

            ResponseEntity<String> response = api.get(api.ordersUri(credential, pageQuery), credential);
            Map<String, Object> root = rootOf(response.getBody());
            List<Map<String, Object>> results = PlatformJson.objects(PlatformJson.path(root, MercadoApi.RESULTS_KEY));
            Integer total = PlatformJson.integer(root, MercadoApi.PAGING_KEY, "total");
            boolean anyNew = false;
            for (Map<String, Object> order : results) {
                Map<String, Object> row = toRow(order);
                // 平台跨页重复返回同一单不能重复出行，否则导入侧按幂等键会互相覆盖
                if (!markSeen(seenOrderIds, row.get("orderId"))) {
                    continue;
                }
                anyNew = true;
                if (wantedOrderId == null || wantedOrderId.equals(row.get("orderId"))) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            pages++;
            if (results.isEmpty()) {
                hasMore = false;
            } else if (!anyNew) {
                // 整页都是见过的单 = 平台没有按 offset 推进（或同一页被重复返回）；继续翻只会白耗 429 配额
                log.warn("[Mercado] 第 {} 页取回 {} 条但全是重复订单，已停止翻页（offset={} 可能未被平台遵守）",
                        pages, results.size(), offset);
                hasMore = false;
            } else {
                offset += pageLimit;
                hasMore = total == null || offset < total;
            }
        }
        if (hasMore) {
            log.warn("[Mercado] 订单拉取在翻页上限 {} 或条数上限 {} 处收手，可能仍有后续页未取回 platformOrders={}",
                    api.maxPages(), totalLimit, rows.size());
        }
        log.info("[Mercado] 订单拉取完成 pages={} rows={} host={}", pages, rows.size(), api.host(credential));
        return rows;
    }

    /**
     * 把 CBT 订单搜索行映射为导入侧的中立行。源键全部是候选探测（§7 未核实订单对象结构），探不到就留空。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = PlatformJson.linkedRow();
        Map<String, Object> buyer = PlatformJson.nested(order, "buyer");
        row.put("orderId", firstIdText(order, ORDER_ID_KEYS));
        row.put("customerName", firstText(buyer, CUSTOMER_NAME_KEYS));
        row.put("email", PlatformJson.text(buyer, "email"));
        row.put("orderStatus", bridgeStatus(firstText(order, ORDER_STATUS_KEYS)));
        row.put("orderTime", PlatformJson.toImportDateTime(firstText(order, ORDER_TIME_KEYS)));
        row.put("currency", firstText(order, CURRENCY_KEYS));
        row.put("totalAmount", firstText(order, TOTAL_AMOUNT_KEYS));
        row.put("paymentMethod", PlatformJson.text(order, "payment_method"));
        row.put("paymentStatus", PlatformJson.text(order, "payment_status"));
        // 地址七个键留空的原因见类注释：来源端点核实了，但字段名与订单行里指向货件的关联键都没核实，不做无据探测
        for (String addressKey : UNRESOLVED_ADDRESS_KEYS) {
            row.put(addressKey, null);
        }
        // 明细数组原样透传：§7 只核实到 order_items/items 这一层键名候选，元素结构未核实，实测后在此处一次性对齐
        row.put("lineItems", PlatformJson.objects(firstListNode(order, LINE_ITEM_KEYS)));
        return row;
    }

    /**
     * 状态桥接：§7 核实到的枚举是<b>货件</b>状态（订单对象自身的 {@code status} 取值集合未核实，ISS-065），
     * 下表按该枚举 + ERP {@code OrderStatus} 的六个码给出：
     * <pre>
     *   pending                      -&gt; pending     待付款/未处理
     *   ready_to_ship                -&gt; processing  已收款待发货
     *   shipped                      -&gt; shipped     已在途
     *   delivered                    -&gt; completed   已签收
     *   cancelled                    -&gt; cancelled
     *   其他（如订单侧的 paid/not_paid/withdrawn 等未核实取值）-&gt; 原样透出
     * </pre>
     * 认不出的一律原样透出而不是硬塞进 {@code pending}：导入侧 {@code toOrderStatus} 会兜底成待处理，
     * 但保留原文才能让人看见平台侧出现了新的状态值。比较前把下划线/连字符归一为空格，
     * 且必须按完整取值匹配——{@code shipped} 与 {@code ready_to_ship} 互为前缀片段，用「包含」会判错。
     */
    static String bridgeStatus(String mercadoStatus) {
        if (mercadoStatus == null || mercadoStatus.isBlank()) {
            return null;
        }
        String value = mercadoStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ').replace('-', ' ');
        return switch (normalized) {
            case "pending" -> "pending";
            case "ready to ship" -> "processing";
            case "shipped" -> "shipped";
            case "delivered" -> "completed";
            case "cancelled", "canceled" -> "cancelled";
            default -> value;
        };
    }

    /**
     * §7 的增量窗口：{@code last_updated.gte}/{@code last_updated.lt}，值为<b>带 UTC 偏移的 ISO-8601 字符串</b>。
     * 两个界总是成对发出：请求只给了一侧就由另一侧补出来（缺下界 → 上界往前 30 天；缺上界 → 当前 UTC 时刻）。
     * CBT 搜索没有「完全不带时间界」的全量档可依赖，发一个有界窗口比赌平台接受空参数稳妥。
     * <p>
     * {@code date_created.gte|lt} 同为 §7 核实的参数，但增量同步按平台侧更新时间取，故不发。
     */
    private void putTimeWindow(Map<String, String> query, PlatformOrderFetchRequest request) {
        LocalDateTime until = request.updatedTo() == null
                ? LocalDateTime.now(ZoneOffset.UTC) : request.updatedTo();
        LocalDateTime since = request.updatedFrom() == null
                ? until.minusDays(DEFAULT_BACKFILL_DAYS) : request.updatedFrom();
        query.put(MercadoApi.LAST_UPDATED_GTE_PARAM, MercadoApi.offsetTimestamp(since));
        query.put(MercadoApi.LAST_UPDATED_LT_PARAM, MercadoApi.offsetTimestamp(until));
    }

    /**
     * 解出响应里的 {@code results} 数组（连通性测试也复用）；空响应体按「没有订单」处理而不是结构错误。
     */
    static List<Map<String, Object>> resultsIn(String jsonBody) {
        return PlatformJson.objects(PlatformJson.path(rootOf(jsonBody), MercadoApi.RESULTS_KEY));
    }

    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    private static Object firstListNode(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            Object value = PlatformJson.path(node, key);
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    /**
     * 候选键逐个探：{@code PlatformJson.text(node, "a", "b")} 是「{@code a} 再 {@code a.b}」的下钻而不是二选一，
     * 所以这里必须自己循环（同 {@code AmazonOrderFetcher.firstText}）。
     */
    private static String firstText(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstIdText(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.idText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeOrderNumber(String orderNumber) {
        return orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
    }

    /**
     * 记录已出行过的平台订单号；没有订单号的行不能被当成「已经见过」，交给导入侧的必填校验去拦。
     */
    private static boolean markSeen(Set<String> seenOrderIds, Object orderId) {
        return orderId == null || seenOrderIds.add(String.valueOf(orderId));
    }
}
