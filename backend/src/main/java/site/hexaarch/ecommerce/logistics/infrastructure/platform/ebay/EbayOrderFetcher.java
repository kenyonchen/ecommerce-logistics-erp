package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * eBay Sell 订单拉取适配器：{@code GET /sell/fulfillment/v1/order}（§4.2 {@code getOrders}）。
 * <p>
 * 这个适配器存在的意义就是「按核实版 spec 调用，而不是按散文页」——§0 把它列为推翻预设的三处之一：
 * <ul>
 *   <li>{@code getAllOrders} 端点在当前 spec 已不存在，绝不发；</li>
 *   <li>spec 的 parameters <b>只有</b> {@code fieldGroups|filter|limit|offset|orderIds} 五个，
 *       旧的 {@code order_status=}、{@code creation_date_range=}、{@code modification_date_range=} 独立参数键
 *       都不在其中，按它们发等于发不存在的参数——所有过滤一律走单个 {@code filter} 参数；</li>
 *   <li>增量窗口用 {@code filter=lastmodifieddate:[<ISO>..<ISO>]}（§4.2 区间语法，两字点分隔）；
 *       {@code limit} 夹到 1..200（>200 平台报错）。</li>
 * </ul>
 * 翻页只跟随响应体的 {@code next}（§4.2：{@code offset} 到底是页码还是记录数没写明，故不自算 {@code offset}），
 * 且对 {@code next} 做 host 归属校验，不盲发平台返回的任意绝对 URL。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§4.2 核实的是端点与查询参数，不是订单对象结构。
 * 因此本类对「订单数组在信封里的位置」「时间/金额/买家/收件字段」一律按候选键探测，探测不到就留空而不是断言；
 * 这些待实测项归入 ISS-065。输出的行键与 {@code ShopifyOrderFetcher}/{@code AmazonOrderFetcher} 同一套中立别名。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class EbayOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 200;

    /**
     * 没有下界时的回溯窗口（天），沿用 Amazon 的同名口径，覆盖常规同步节奏又不至于放大分页成本
     */
    private static final int DEFAULT_BACKFILL_DAYS = 30;

    /**
     * 订单数组在响应信封里的候选键；{@code getOrders} 集合键名未被核实（REST 惯例是单数 {@code order}），只探测不假定
     */
    static final String[] ORDERS_ARRAY_KEYS = {"order", "orders"};

    private final EbaySellApi api;

    public EbayOrderFetcher(EbaySellApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.EBAY_SELL.name();
    }

    /**
     * eBay 必须要 Bearer 访问令牌，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("eBay Sell 订单适配器需要凭据上下文(accessToken)，请经适配器注册表调用",
                "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        Map<String, String> query = new LinkedHashMap<>();
        if (wantedOrderId != null) {
            // §4.2：orderIds 是 spec 里的合法参数，精确取单用它，而不是自己按 filter 拼 id
            query.put("orderIds", wantedOrderId);
        } else {
            query.put("filter", lastModifiedFilter(request));
        }
        query.put("limit", String.valueOf(EbaySellApi.clampLimit(request.limit())));

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        String configuredHost = api.host(credential);
        String base = api.base(credential);
        URI current = api.ordersUri(credential, query);
        Set<String> requestedUrls = new HashSet<>();
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            String currentUrl = current.toString();
            // next 指回已经请求过的 URL 即视为游标不推进，立即收手，避免在页数上限上空转
            if (!requestedUrls.add(currentUrl)) {
                break;
            }
            ResponseEntity<String> response = api.get(current, credential);
            Map<String, Object> root = rootOf(response.getBody());
            for (Map<String, Object> order : ordersFrom(root)) {
                Map<String, Object> row = toRow(order);
                // 平台跨页重复返回同一单不能重复出行，否则导入侧按幂等键会互相覆盖
                if (!markSeen(seenOrderIds, row.get("orderId"))) {
                    continue;
                }
                if (wantedOrderId == null || wantedOrderId.equals(row.get("orderId"))) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            pages++;
            URI next = toTrustedNext(nextFrom(root), configuredHost, base);
            hasMore = next != null && !next.toString().equals(currentUrl);
            current = next;
        }
        if (hasMore) {
            log.warn("[eBay] 订单拉取达到翻页上限 {}，尚有后续页未取回 platformOrders={}", api.maxPages(), rows.size());
        }
        log.info("[eBay] 订单拉取完成 pages={} rows={} host={}", pages, rows.size(), configuredHost);
        return rows;
    }

    /**
     * 把 eBay 订单对象映射为导入侧的中立行。除别名键名外，所有源字段都是候选探测（§4.2 未核实对象结构）。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = new LinkedHashMap<>();
        Map<String, Object> buyer = PlatformJson.nested(order, "buyer");
        Map<String, Object> shipTo = PlatformJson.nested(order, "shipTo");
        Map<String, Object> address = PlatformJson.nested(shipTo, "address");
        Map<String, Object> contact = PlatformJson.nested(shipTo, "primaryContact");
        Map<String, Object> payment = PlatformJson.nested(order, "paymentSummary");

        row.put("orderId", PlatformJson.idText(order, "orderId"));
        row.put("customerName", PlatformJson.text(buyer, "username"));
        row.put("email", firstNonBlank(PlatformJson.text(buyer, "email"), PlatformJson.text(order, "email")));
        // 状态与金额两处的桥接口径见 bridgeStatus / readTotal 的判定表注释
        row.put("orderStatus", bridgeStatus(firstNonBlank(PlatformJson.text(order, "orderStatus"),
                PlatformJson.text(PlatformJson.nested(order, "legacyOrder"), "status"))));
        row.put("orderTime", PlatformJson.toImportDateTime(firstNonBlank(
                readTime(order, "createdTime"), readTime(order, "creationDate"),
                readTime(PlatformJson.nested(order, "legacyOrder"), "createdDate"))));
        row.put("currency", firstNonBlank(readCurrency(order, "totalAmount"), readCurrency(order, "amountPaid"),
                PlatformJson.text(order, "pricingSummary", "total", "currency"), PlatformJson.text(order, "currency")));
        row.put("totalAmount", firstNonBlank(readAmount(order, "totalAmount"), readAmount(order, "amountPaid"),
                PlatformJson.text(order, "pricingSummary", "total", "value")));
        row.put("paymentMethod", firstNonBlank(PlatformJson.text(payment, "paymentMethod"),
                PlatformJson.text(order, "paymentMethod")));
        row.put("paymentStatus", firstNonBlank(PlatformJson.text(payment, "status"),
                PlatformJson.text(order, "paymentStatus")));
        row.put("recipientName", firstNonBlank(PlatformJson.text(contact, "name"), PlatformJson.text(address, "name"),
                PlatformJson.text(shipTo, "name")));
        row.put("phoneNumber", firstNonBlank(PlatformJson.text(contact, "phone"), PlatformJson.text(address, "phone")));
        row.put("country", firstNonBlank(PlatformJson.text(address, "country"),
                PlatformJson.text(address, "addressCountry")));
        row.put("province", firstNonBlank(PlatformJson.text(address, "stateOrProvince"),
                PlatformJson.text(address, "province")));
        row.put("city", PlatformJson.text(address, "city"));
        row.put("addressLine1", firstNonBlank(PlatformJson.text(address, "addressLine1"),
                PlatformJson.text(address, "street")));
        row.put("zipCode", firstNonBlank(PlatformJson.text(address, "postalCode"),
                PlatformJson.text(address, "addressPostalCode"), PlatformJson.text(address, "zip")));
        // 明细数组原样透传：元素结构未核实，发货回写再从 orderLineItems[].lineItem.id 取履行行
        row.put("lineItems", PlatformJson.objects(PlatformJson.path(order, "orderLineItems")));
        return row;
    }

    /**
     * 状态桥接：eBay fulfillment 域的状态字段名与取值集合都未被 §4.2 核实，下表按 eBay 文档常见枚举推断，
     * 待实测校正（ISS-065）。判定顺序不可调换——{@code AWAITING_FULFILLMENT}/{@code UNFULFILLED} 字面含
     * {@code fulfill}，必须先于「已发货」归类；{@code cancelled/refund} 最优先。
     * 认不出的一律原样透出而不是硬塞进 {@code pending}：保留原文才能让人看见平台侧出现了新的状态值。
     * <pre>
     *   CANCEL* / *REFUND*            -> cancelled
     *   AWAITING* / *UNPAID / PENDING -> pending        （未付款或待处理）
     *   *UNFULFILLED / *UNSHIPPED     -> processing      （已付款待发货）
     *   COMPLETED / FULFILLED / SHIPPED -> shipped
     *   *PAID                         -> processing
     *   其他                           -> 原样透出
     * </pre>
     */
    static String bridgeStatus(String ebayStatus) {
        if (ebayStatus == null || ebayStatus.isBlank()) {
            return null;
        }
        String value = ebayStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ').replace('-', ' ');
        if (normalized.contains("cancel") || normalized.contains("refund")) {
            return "cancelled";
        }
        if (normalized.contains("awaiting") || normalized.contains("unpaid") || normalized.contains("pending")) {
            return "pending";
        }
        if (normalized.contains("unfulfilled") || normalized.contains("unshipped")) {
            return "processing";
        }
        if (normalized.contains("completed") || normalized.contains("fulfilled") || normalized.contains("shipped")) {
            return "shipped";
        }
        if (normalized.contains("paid")) {
            return "processing";
        }
        return value;
    }

    /**
     * 增量窗口 {@code lastmodifieddate:[from..to]}（§4.2 区间语法）。两个界都缺时按 {@code now} 收尾并回溯
     * {@value #DEFAULT_BACKFILL_DAYS} 天——和 Amazon 一样，没有时间界就给了一个有界窗口而不是裸拉全量。
     */
    private String lastModifiedFilter(PlatformOrderFetchRequest request) {
        LocalDateTime to = request.updatedTo() != null ? request.updatedTo() : LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = request.updatedFrom() != null ? request.updatedFrom() : to.minusDays(DEFAULT_BACKFILL_DAYS);
        return "lastmodifieddate:[" + PlatformJson.toIsoUtcQueryTimestamp(from)
                + ".." + PlatformJson.toIsoUtcQueryTimestamp(to) + "]";
    }

    /**
     * 解出响应里的订单数组（连通性测试与发货回写也复用）。
     */
    static List<Map<String, Object>> ordersIn(String jsonBody) {
        return ordersFrom(rootOf(jsonBody));
    }

    private static List<Map<String, Object>> ordersFrom(Map<String, Object> root) {
        for (String key : ORDERS_ARRAY_KEYS) {
            List<Map<String, Object>> rows = PlatformJson.objects(root.get(key));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    /**
     * 取响应体的续页 URL：{@code next} 是字符串直接返回，包成对象时退到其 {@code href}；没有就返回 {@code null}。
     */
    private static String nextFrom(Map<String, Object> root) {
        Object next = root.get("next");
        if (next instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (next instanceof Map<?, ?> map) {
            Object href = map.get("href");
            if (href instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * 把响应体给的 {@code next} 变成可信任的续页 URI：
     * <ul>
     *   <li>host 与配置一致 —— eBay 自己生成的绝对续页 URL，整体跟随（其中已含平台算好的 offset/limit）；</li>
     *   <li>host 不符或未给出 —— <b>绝不盲发外部绝对 URL</b>，只取 path+query 重新钉到配置 base 的 scheme/host 上。
     *       选择「重钉」而非「拒绝」是因为 §4.3 记「各市场共用 api.ebay.com 未明文」，next 主机可能因路由/CDN
     *       与配置的沙箱 host 字面不同；path+query 是平台对「下一页」的真实编码，值得保留，主机名则交给我们的配置。</li>
     * </ul>
     */
    static URI toTrustedNext(String nextText, String configuredHost, String base) {
        if (nextText == null || nextText.isBlank()) {
            return null;
        }
        URI parsed;
        try {
            parsed = URI.create(nextText.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String nextHost = parsed.getHost();
        if (nextHost != null && configuredHost != null && configuredHost.equalsIgnoreCase(nextHost)) {
            return parsed;
        }
        String path = parsed.getRawPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(base)
                .replacePath(path)
                .replaceQuery(parsed.getRawQuery())
                .build(true)
                .toUri();
    }

    /**
     * 2xx 空响应体按「没有订单」处理而不是结构错误——连通性测试只关心调用是否被平台接受。
     */
    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    /**
     * 金额字段可能是标量字符串，也可能是 {@code {value, currency}} 对象：只取 value/amount，取不到留空不臆造。
     */
    private static String readAmount(Map<String, Object> order, String key) {
        Object value = PlatformJson.path(order, key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (value instanceof Map<?, ?> map) {
            return firstTextFrom(map, "value", "amount");
        }
        return null;
    }

    private static String readCurrency(Map<String, Object> order, String key) {
        Object value = PlatformJson.path(order, key);
        if (value instanceof Map<?, ?> map) {
            return firstTextFrom(map, "currency", "currencyCode");
        }
        return null;
    }

    /**
     * 时间字段可能是 ISO 字符串，也可能是 {@code {value}} 包装；只接受字符串形态，避免把对象 toString 当时间。
     * 包级可见供 {@link EbayFulfillmentPusher} 复用同一套探测口径。
     */
    static String readTime(Map<String, Object> node, String key) {
        Object value = PlatformJson.path(node, key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (value instanceof Map<?, ?> map) {
            return firstTextFrom(map, "value", "date");
        }
        return null;
    }

    /**
     * 在候选键里取第一个非空标量（顶层键，非下钻）；{@code text} 的可变参数是下钻路径，不能用于「或」探测。
     */
    private static String firstTextFrom(Map<?, ?> node, String... keys) {
        Map<String, Object> map = asMap(node);
        for (String key : keys) {
            String value = PlatformJson.text(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
