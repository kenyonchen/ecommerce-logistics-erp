package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shopee 订单拉取适配器：{@code POST /api/v2/order/get_order_list} 取单号+概要，再
 * {@code POST /api/v2/order/get_order_detail} 取收件地址与明细。
 * <p>
 * 两步是 §6 硬规定的（docs/spec/platform-api-reference.md §6：收件人明细「要再调 get_order_detail」），
 * 不是可选优化——列表接口不返回收件地址，只调列表则每单的 {@code shipping}/{@code buyer}/{@code item_list}
 * 全拿不到，导入侧的必填校验会整批失败。明细调用还必须<b>显式列 {@code response_optional_fields}</b>
 * （{@code shipping,buyer,invoice,item_list}），否则该节点根本不在响应里（§6 点名的坑）。
 * <p>
 * §6 另一条必须由代码兜住的坑：时间窗 {@code time_from}/{@code time_to} 的<b>跨度硬上限 15 天</b>。
 * 本类把超出 15 天的窗口就地把 {@code time_to} 夹到 {@code time_from + 15 天}，并 WARN 出被截断的实际窗口，
 * 让操作员看到「有一段数据没取回」——而不是在一次调用里静默循环多个窗口（那会把 15 天上限绕成隐形全量拉取）。
 * <p>
 * 翻页用 §6 的 {@code cursor}：响应 {@code more} 为真且 {@code next_cursor} 存在时跟游标，
 * {@code next_cursor} 与本页所用游标相同（不推进）时收手，避免在页数上限上空转。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§6 核实的是端点、必填参数与信封（{@code {request_id,error,message,response}}），
 * 不是 {@code response} 里订单对象的结构。因此订单数组的键（{@code order_list}/{@code orders}）、单号键
 * （{@code order_sn}）、金额/收件人/明细键一律按候选探测，探不到就留空而不是断言某种形状；这些待实测项归入 ISS-065。
 * 输出的行键对齐 {@code OrderImportTargetField} 别名表，与 {@code AmazonOrderFetcher}/{@code OzonOrderFetcher} 同一套中立形状。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopeeOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * §6 要求 {@code time_from}/{@code time_to} 都必填，但没给平台允许的 {@code page_size} 上限；
     * 这里取一个保守的本地值（Shopee 侧常见上限 30），超出的请求条数靠游标翻页而不是一次塞满（ISS-065）。
     */
    private static final int PAGE_SIZE = 30;

    /**
     * 没有时间界时的默认回溯窗口（天），刻意 ≤ §6 的 15 天上限以免触发截断
     */
    private static final int DEFAULT_WINDOW_DAYS = 15;

    /**
     * 订单数组在 {@code response} 里的候选键：§6 未核实其命名，只探测不假定
     */
    private static final String[] ORDER_ARRAY_KEYS = {"order_list", "orders"};

    /**
     * 明细里必须显式请求的附加块（§6：不写就永远不返回地址）
     */
    static final String RESPONSE_OPTIONAL_FIELDS = "shipping,buyer,invoice,item_list";

    private final ShopeeOpenApi api;

    public ShopeeOrderFetcher(ShopeeOpenApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPEE.name();
    }

    /**
     * Shopee 必须签名（partner_id/partner_key/shop_id/access_token），单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Shopee 订单适配器需要凭据上下文(apiKey=partner_id、apiSecret=partner_key、"
                + "externalShopId=shop_id、accessToken)，请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        String cursor = null;
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            Map<String, Object> body = listBody(request, cursor);
            Map<String, Object> envelope = ShopeeOpenApi.readEnvelope(
                    api.postJson(credential, ShopeeOpenApi.ORDER_LIST_PATH, body).getBody(),
                    ShopeeOpenApi.ORDER_LIST_PATH);
            Map<String, Object> response = ShopeeOpenApi.response(envelope);
            List<Map<String, Object>> summaries = orderArray(response);

            Map<String, Map<String, Object>> details = detailsFor(credential, summaries);
            for (Map<String, Object> summary : summaries) {
                String orderId = firstId(summary, "order_sn", "ordersn", "order_id");
                Map<String, Object> detail = orderId == null ? Map.of()
                        : details.getOrDefault(orderId, Map.of());
                String rowOrderId = firstNonBlank(orderId, firstId(detail, "order_sn"));
                if (rowOrderId == null || !seenOrderIds.add(rowOrderId)) {
                    continue;
                }
                if (wantedOrderId != null && !wantedOrderId.equals(rowOrderId)) {
                    continue;
                }
                rows.add(toRow(summary, detail));
                if (rows.size() >= totalLimit) {
                    break;
                }
            }
            pages++;
            String appliedCursor = cursor;
            String nextCursor = probeText(response, "next_cursor", "cursor");
            hasMore = truthy(response.get("more")) && nextCursor != null
                    && !nextCursor.equals(appliedCursor);
            cursor = hasMore ? nextCursor : null;
        }
        if (hasMore) {
            log.warn("[Shopee] 订单拉取达到翻页上限 {}，尚有后续页未取回 platformOrders={}", api.maxPages(), rows.size());
        }
        log.info("[Shopee] 订单拉取完成 pages={} rows={} shop_id={}", pages, rows.size(),
                credential == null ? null : credential.externalShopId());
        return rows;
    }

    /**
     * 列表/概要 + 明细合并成导入侧中立行。概要给单号/状态/时间/金额，明细给收件地址/买家/明细行；
     * 明细缺失（详情接口没回该单）时地址类字段留空，由导入侧必填校验去报这一行。
     */
    Map<String, Object> toRow(Map<String, Object> summary, Map<String, Object> detail) {
        Map<String, Object> row = PlatformJson.linkedRow();
        Map<String, Object> shipping = probeNested(detail, "shipping", "address_shipping");
        Map<String, Object> buyer = probeNested(detail, "buyer");

        row.put("orderId", firstNonBlank(firstId(summary, "order_sn", "ordersn", "order_id"),
                firstId(detail, "order_sn", "ordersn")));
        row.put("customerName", firstNonBlank(probeText(buyer, "username", "account_name"),
                probeText(summary, "buyer_username", "buyer")));
        row.put("email", firstNonBlank(PlatformJson.text(buyer, "email"),
                PlatformJson.text(summary, "email")));
        row.put("orderStatus", bridgeStatus(firstNonBlank(probeText(summary, "order_status", "status"),
                probeText(detail, "order_status", "status"))));
        // create_time 是 epoch 秒（§6 时间口径），导入侧只认 yyyy-MM-dd HH:mm:ss
        row.put("orderTime", PlatformJson.epochSecondsToImportDateTime(
                firstNonNull(summary.get("create_time"), detail.get("create_time"))));
        row.put("currency", firstNonBlank(PlatformJson.text(summary, "currency"),
                PlatformJson.text(detail, "currency")));
        row.put("totalAmount", firstNonBlank(probeDecimal(summary, "total_amount", "order_amount", "amount"),
                probeDecimal(detail, "total_amount", "order_amount")));
        row.put("paymentMethod", firstNonBlank(PlatformJson.text(summary, "payment_method"),
                PlatformJson.text(detail, "payment_method")));
        row.put("paymentStatus", firstNonBlank(probeText(summary, "escrow_status", "payment_status"),
                probeText(detail, "escrow_status", "payment_status")));
        row.put("recipientName", firstNonBlank(probeText(shipping, "name", "full_name"),
                PlatformJson.text(buyer, "username"), probeText(summary, "buyer_username", "buyer")));
        row.put("phoneNumber", firstNonBlank(probeText(shipping, "phone", "mobile"),
                PlatformJson.text(buyer, "phone")));
        row.put("country", firstNonBlank(probeText(shipping, "country", "country_code"),
                PlatformJson.text(detail, "country")));
        row.put("province", firstNonBlank(probeText(shipping, "state", "province", "region"),
                PlatformJson.text(detail, "state")));
        row.put("city", probeText(shipping, "city", "town"));
        row.put("addressLine1", firstNonBlank(probeText(shipping, "address", "address_detail"),
                PlatformJson.text(detail, "address")));
        row.put("zipCode", firstNonBlank(probeText(shipping, "zip_code", "postal_code"),
                PlatformJson.text(detail, "zip_code")));
        // 明细数组原样透传：item_list 的元素结构未被 §6 核实，实测后在此处一次性对齐
        row.put("lineItems", PlatformJson.objects(firstNonNull(
                PlatformJson.path(detail, "item_list"), PlatformJson.path(detail, "items"))));
        return row;
    }

    /**
     * 状态桥接：Shopee 的 {@code order_status} 是平台自有枚举，ERP 侧只认 {@code OrderStatus} 六个码。
     * 下表按 Shopee v2 公开枚举整理（§6 记录了状态字段存在，但未逐个核实取值 → ISS-065 以实测为准）：
     * <pre>
     *   UNPAID                                   -&gt; pending     未付款
     *   TO_CONFIRM / TO_SHIP                     -&gt; processing  待确认/待发货（卖家侧待办）
     *   TO_ARRIVE                                -&gt; shipped     已发货在途
     *   COMPLETED                                -&gt; completed   已签收
     *   CANCELLED / IN_CANCEL                    -&gt; cancelled   已取消/取消中
     *   其他                                      -&gt; 原样透出
     * </pre>
     * 认不出的一律原样透出而不是硬塞 {@code pending}：导入侧 {@code toOrderStatus} 会兜底成待处理，
     * 但保留原文才能让人看见平台侧出现了新的状态值。归一化后按完整取值精确匹配，不用「包含」。
     */
    static String bridgeStatus(String shopeeStatus) {
        if (shopeeStatus == null || shopeeStatus.isBlank()) {
            return null;
        }
        String value = shopeeStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ');
        return switch (normalized) {
            case "unpaid" -> "pending";
            case "to confirm", "to ship" -> "processing";
            case "to arrive" -> "shipped";
            case "completed" -> "completed";
            case "cancelled", "in cancel" -> "cancelled";
            default -> value;
        };
    }

    /**
     * 列表请求体：{@code time_range_field}/{@code time_from}/{@code time_to}/{@code page_size} 必填（§6），
     * {@code cursor} 续页时带上。有 {@code updatedFrom()} 走 {@code update_time}（增量同步），否则 {@code create_time}。
     * 超出 §6 的 15 天跨度上限时把 {@code time_to} 就地夹到 {@code time_from + 15 天} 并 WARN。
     */
    private Map<String, Object> listBody(PlatformOrderFetchRequest request, String cursor) {
        boolean incremental = request.updatedFrom() != null;
        LocalDateTime to = request.updatedTo() != null
                ? request.updatedTo() : LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = incremental ? request.updatedFrom() : to.minusDays(DEFAULT_WINDOW_DAYS);
        LocalDateTime cappedTo = from.plusDays(ShopeeOpenApi.MAX_TIME_RANGE_DAYS);
        if (to.isAfter(cappedTo)) {
            log.warn("[Shopee] 订单时间窗超出 §6 的 {} 天上限，已把 time_to 由 {} 截断到 {}（{} 之后的更新未取回）",
                    ShopeeOpenApi.MAX_TIME_RANGE_DAYS, to, cappedTo, cappedTo);
            to = cappedTo;
        }
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("time_range_field", incremental ? "update_time" : "create_time");
        body.put("time_from", PlatformJson.toEpochSeconds(from));
        body.put("time_to", PlatformJson.toEpochSeconds(to));
        body.put("page_size", PAGE_SIZE);
        if (cursor != null) {
            body.put("cursor", cursor);
        }
        return body;
    }

    /**
     * 一页概要对应的明细回取：一次 {@code get_order_detail} 带本页全部 {@code order_sn_list[]} +
     * 显式 {@code response_optional_fields}（§6：不带就没有地址），返回 {@code order_sn → 明细对象}。
     */
    private Map<String, Map<String, Object>> detailsFor(PlatformCredential credential, List<Map<String, Object>> summaries) {
        List<String> orderSns = new ArrayList<>();
        for (Map<String, Object> summary : summaries) {
            String sn = firstId(summary, "order_sn", "ordersn", "order_id");
            if (sn != null && !orderSns.contains(sn)) {
                orderSns.add(sn);
            }
        }
        Map<String, Map<String, Object>> byOrder = new LinkedHashMap<>();
        if (orderSns.isEmpty()) {
            return byOrder;
        }
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("order_sn_list", orderSns);
        body.put("response_optional_fields", RESPONSE_OPTIONAL_FIELDS);
        Map<String, Object> envelope = ShopeeOpenApi.readEnvelope(
                api.postJson(credential, ShopeeOpenApi.ORDER_DETAIL_PATH, body).getBody(),
                ShopeeOpenApi.ORDER_DETAIL_PATH);
        for (Map<String, Object> order : orderArray(ShopeeOpenApi.response(envelope))) {
            String sn = firstId(order, "order_sn", "ordersn", "order_id");
            if (sn != null) {
                byOrder.put(sn, order);
            }
        }
        return byOrder;
    }

    /**
     * 解出信封里当前页的订单数组（连通性测试也复用）：探测 {@code response.order_list}/{@code response.orders}，
     * 探不到返回空列表（视为该窗没有订单而不是结构错误）。
     */
    static List<Map<String, Object>> ordersIn(String jsonBody) {
        Map<String, Object> envelope = ShopeeOpenApi.readEnvelope(jsonBody, ShopeeOpenApi.ORDER_LIST_PATH);
        return orderArray(ShopeeOpenApi.response(envelope));
    }

    private static List<Map<String, Object>> orderArray(Map<String, Object> response) {
        for (String key : ORDER_ARRAY_KEYS) {
            Object value = response.get(key);
            if (value instanceof List<?>) {
                return PlatformJson.objects(value);
            }
        }
        return List.of();
    }

    /**
     * 候选键探测：逐个键单取（{@link PlatformJson#text} 的变参是<b>逐层下钻</b>而非候选表，
     * 所以这里显式循环），返回第一个非空的文本值。响应体键名未被 §6 核实（ISS-065），按候选探测而不是断定一种。
     */
    private static String probeText(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String probeDecimal(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.decimalText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> probeNested(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            if (node.get(key) instanceof Map<?, ?>) {
                return PlatformJson.nested(node, key);
            }
        }
        return Map.of();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)));
    }

    private static String firstId(Map<String, Object> node, String... keys) {
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

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
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
