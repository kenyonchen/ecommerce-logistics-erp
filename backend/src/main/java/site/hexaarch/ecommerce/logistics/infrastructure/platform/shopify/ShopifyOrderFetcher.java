package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shopify 订单拉取适配器：{@code GET /admin/api/{version}/orders.json}。
 * <p>
 * 与通用自建店铺适配器的三点关键差异（都是官方文档规定的，不是可选实现）：
 * <ol>
 *   <li>鉴权走 {@code X-Shopify-Access-Token}；</li>
 *   <li>增量用 {@code updated_at_min}/{@code updated_at_max}（ISO8601，UTC）；</li>
 *   <li>翻页只认响应 {@code Link} 头里的 {@code page_info} 游标，绝不传 {@code page} 参数。</li>
 * </ol>
 * 输出的行键名对齐 {@code OrderImportTargetField} 的别名表，因此字段映射与示例数据链路可复用；
 * Shopify 的 {@code customer}/{@code shipping_address} 是嵌套对象，这里展平成标量键，
 * {@code line_items} 原样透传（明细侧 {@code OrderFieldMappingService#toItems} 认 {@code sku/title/quantity/price}）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopifyOrderFetcher implements PlatformOrderFetcher {

    private static final String ORDERS_RESOURCE = "orders.json";
    private static final int DEFAULT_TOTAL_LIMIT = 100;

    private final ShopifyAdminApi api;

    public ShopifyOrderFetcher(ShopifyAdminApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPIFY.name();
    }

    /**
     * Shopify 必须有访问令牌才能调用，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Shopify 订单适配器需要凭据上下文(accessToken)，请经适配器注册表调用",
                "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("limit", String.valueOf(Math.min(totalLimit, 250)));
        putIfPresent(query, "updated_at_min", ShopifyJson.toQueryTimestamp(request.updatedFrom()));
        putIfPresent(query, "updated_at_max", ShopifyJson.toQueryTimestamp(request.updatedTo()));
        // 平台订单号精确拉取：Shopify 无按订单号查询参数，只能取回后过滤（单号即订单 id）
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        List<Map<String, Object>> rows = new ArrayList<>();
        URI next = api.firstRequestUri(credential, ORDERS_RESOURCE, query);
        int pages = 0;
        while (next != null && pages < api.maxPages() && rows.size() < totalLimit) {
            ResponseEntity<String> response = api.get(next, credential);
            for (Map<String, Object> order : ShopifyJson.collection(response.getBody(), "orders")) {
                Map<String, Object> row = toRow(order);
                if (wantedOrderId == null || wantedOrderId.equals(row.get("orderId"))) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            pages++;
            next = cursorUrl(response, credential);
        }
        if (next != null) {
            log.warn("[Shopify] 订单拉取达到游标页数上限 {}，尚有后续页未取回 platformOrders={}", api.maxPages(), rows.size());
        }
        log.info("[Shopify] 订单拉取完成 pages={} rows={} store={}", pages, rows.size(),
                credential == null ? null : credential.storeUrl());
        return rows;
    }

    /**
     * 把 Shopify 订单对象映射为导入侧的中立行。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", ShopifyJson.idText(order, "id"));
        row.put("customerName", customerName(order));
        row.put("email", firstNonBlank(ShopifyJson.text(order, "email"), ShopifyJson.text(order, "customer", "email")));
        row.put("orderStatus", bridgeStatus(order));
        row.put("orderTime", ShopifyJson.toImportDateTime(ShopifyJson.text(order, "created_at")));
        row.put("currency", ShopifyJson.text(order, "currency"));
        row.put("totalAmount", ShopifyJson.text(order, "total_price"));
        row.put("paymentMethod", ShopifyJson.text(order, "payment_details", "gateway"));
        row.put("paymentStatus", ShopifyJson.text(order, "financial_status"));
        putAddress(row, order);
        row.put("lineItems", ShopifyJson.objects(ShopifyJson.path(order, "line_items")));
        return row;
    }

    /**
     * 状态桥接：Shopify 用 {@code financial_status} + {@code fulfillment_status} + {@code cancelled_at}
     * 三个字段表达订单状态，ERP 侧是单一 {@code OrderStatus} 枚举，必须显式建表而不是靠名字猜。
     */
    static String bridgeStatus(Map<String, Object> order) {
        if (ShopifyJson.text(order, "cancelled_at") != null) {
            return "cancelled";
        }
        String fulfillment = lower(ShopifyJson.text(order, "fulfillment_status"));
        if ("fulfilled".equals(fulfillment)) {
            return "shipped";
        }
        String financial = lower(ShopifyJson.text(order, "financial_status"));
        if (financial == null) {
            return "pending";
        }
        return switch (financial) {
            case "paid" -> "processing";
            case "refunded", "voided" -> "cancelled";
            default -> "pending";
        };
    }

    private String customerName(Map<String, Object> order) {
        String first = ShopifyJson.text(order, "customer", "first_name");
        String last = ShopifyJson.text(order, "customer", "last_name");
        String joined = firstNonBlank(combine(first, last), ShopifyJson.text(order, "customer", "name"));
        return firstNonBlank(joined, ShopifyJson.text(order, "shipping_address", "name"));
    }

    private void putAddress(Map<String, Object> row, Map<String, Object> order) {
        String recipient = ShopifyJson.text(order, "shipping_address", "name");
        row.put("recipientName", firstNonBlank(recipient,
                combine(ShopifyJson.text(order, "shipping_address", "first_name"),
                        ShopifyJson.text(order, "shipping_address", "last_name")),
                ShopifyJson.text(order, "billing_address", "name")));
        row.put("phoneNumber", ShopifyJson.text(order, "shipping_address", "phone"));
        row.put("country", ShopifyJson.text(order, "shipping_address", "country"));
        row.put("province", ShopifyJson.text(order, "shipping_address", "province"));
        row.put("city", ShopifyJson.text(order, "shipping_address", "city"));
        row.put("addressLine1", firstNonBlank(ShopifyJson.text(order, "shipping_address", "address1"),
                ShopifyJson.text(order, "shipping_address", "address2")));
        row.put("zipCode", ShopifyJson.text(order, "shipping_address", "zip"));
    }

    /**
     * 续页 URL 由 {@code Link} 头给出，原样使用（其中已含 {@code page_info} 与 {@code limit}）。
     */
    private URI cursorUrl(ResponseEntity<String> response, PlatformCredential credential) {
        String url = api.nextCursorUrl(response.getHeaders());
        if (url == null || url.isBlank()) {
            return null;
        }
        String absolute = url.startsWith("http") ? url
                : api.adminBase(credential) + (url.startsWith("/") ? url : "/" + url);
        return URI.create(absolute);
    }

    /**
     * Shopify 前台展示的订单号形如 {@code #1001}，API 侧主键是数字 id；两种写法都接受。
     */
    private static String normalizeOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }
        String trimmed = orderNumber.trim();
        return trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
    }

    private static void putIfPresent(Map<String, String> query, String key, String value) {
        if (value != null && !value.isBlank()) {
            query.put(key, value);
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
