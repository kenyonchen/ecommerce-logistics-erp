package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shopify 适配器的字段桥接与发货回写契约测试。
 * <p>
 * 响应体形状取自官方文档的示例结构，经本地 {@link HttpServer} 回放，覆盖「我们怎么解析平台数据」
 * 与「我们怎么回写平台」两侧。<b>未经真实平台验证</b>（当前无沙箱凭据），上线前仍需凭据演练。
 *
 * @author kenyon
 */
class ShopifyAdapterMappingTest {

    /**
     * 官方文档形态的订单：嵌套 customer/shipping_address、带时区偏移的时间戳、字符串金额、变体明细。
     */
    private static final String ORDER_FIXTURE = """
            {"orders":[{
              "id":1001,
              "email":"jane@example.com",
              "created_at":"2026-01-25T08:30:00-04:00",
              "currency":"USD",
              "total_price":"129.90",
              "financial_status":"paid",
              "payment_details":{"gateway":"bogus"},
              "customer":{"first_name":"Jane","last_name":"Doe"},
              "shipping_address":{"name":"Jane Doe","address1":"150 Elgin St",
                                  "city":"Ottawa","province":"Ontario","zip":"K1P 5L6",
                                  "country":"Canada","phone":"613-555-1234"},
              "line_items":[{"id":5001,"sku":"DEMO-SKU","title":"Classic T-Shirt","quantity":2,"price":"24.50"}]
            }]}""";

    private static final String PRODUCT_FIXTURE = """
            {"products":[{
              "id":2001,"title":"Classic Tee","body_html":"<p>Soft cotton &amp; <br>breathable</p>",
              "product_type":"Apparel","status":"active",
              "options":[{"name":"Size","position":1},{"name":"Color","position":2}],
              "variants":[
                {"id":3001,"product_id":2001,"title":"S / Black","price":"19.90","sku":"TEE-S-BLK",
                 "option1":"S","option2":"Black","weight":"0.2","weight_unit":"kg"},
                {"id":3002,"product_id":2001,"title":"M / Black","price":"19.90","sku":"",
                 "option1":"M","option2":"Black","weight":"8","weight_unit":"oz"}
              ]
            }]}""";

    private HttpServer server;
    private String storeUrl;
    private PlatformCredential credential;
    private final List<Recorded> recorded = new ArrayList<>();
    private final List<String> sentBodies = new ArrayList<>();
    private final Deque<Scripted> queue = new ArrayDeque<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.setExecutor(null);
        server.start();
        storeUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        credential = new PlatformCredential(PlatformAuthType.SHOPIFY, "tenant-1", "SHOPIFY", storeUrl,
                "ak", "", "shpat_test", null, "2026-01", null, null, null, null);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------- 订单字段桥接 ----------

    @Test
    void shouldBridgeNestedOrderIntoNeutralRowKeys() {
        List<Map<String, Object>> rows = enqueue(200, ORDER_FIXTURE).fetch(orderRequest(10), credential);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("1001", row.get("orderId"));
        assertEquals("jane@example.com", row.get("email"));
        assertEquals("Jane Doe", row.get("customerName"));
        assertEquals("Jane Doe", row.get("recipientName"));
        assertEquals("150 Elgin St", row.get("addressLine1"));
        assertEquals("K1P 5L6", row.get("zipCode"));
        assertEquals("bogus", row.get("paymentMethod"));
        // -04:00 的 08:30 即 UTC 12:30；导入侧只认 yyyy-MM-dd HH:mm:ss
        assertEquals("2026-01-25 12:30:00", row.get("orderTime"));
        assertEquals("129.90", row.get("totalAmount"));
    }

    @Test
    void shouldEmitOnlyKeysTheAliasTableRecognises() {
        // 原始 Shopify 键名会被模糊映射误配（id->CUSTOMER_ID、name->ITEM_NAME），
        // 所以适配器必须输出导入侧别名表里已有的中立键
        Map<String, Object> row = enqueue(200, ORDER_FIXTURE).fetch(orderRequest(10), credential).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "orderStatus", "orderTime",
                "totalAmount", "recipientName", "lineItems")), row.keySet().toString());
        assertFalse(row.containsKey("financial_status"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("shipping_address"));
    }

    @Test
    void shouldPassLineItemsThroughInKeysTheItemParserAccepts() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) enqueue(200, ORDER_FIXTURE)
                        .fetch(orderRequest(10), credential).get(0).get("lineItems");

        assertEquals(1, items.size());
        assertEquals("DEMO-SKU", items.get(0).get("sku"));
        assertEquals("Classic T-Shirt", items.get(0).get("title"));
        assertEquals(2, items.get(0).get("quantity"));
    }

    @Test
    void shouldYieldBuyerIdentityKeysRecognisableByTheCustomerAliasTable() {
        // 客户同步的 ORDERS 通路复用订单适配器（CustomerImportApplicationService:378），
        // 买家去重键必须能从 Shopify 行里被买家别名表认出来，否则该通路对 Shopify 不成立
        Map<String, Object> row = enqueue(200, ORDER_FIXTURE).fetch(orderRequest(10), credential).get(0);

        Map<CustomerImportTargetField, Object> resolved = new LinkedHashMap<>();
        row.forEach((key, value) -> CustomerImportTargetField.detectByAlias(key)
                .ifPresent(field -> resolved.putIfAbsent(field, value)));

        assertEquals("jane@example.com", resolved.get(CustomerImportTargetField.CUSTOMER_EMAIL),
                "Shopify 行没有平台买家ID时，去重键要退到邮箱");
        assertEquals("Jane Doe", resolved.get(CustomerImportTargetField.CUSTOMER_NAME));
    }

    @Test
    void shouldBridgeStatusFromThreeIndependentFlags() {
        ShopifyOrderFetcher fetcher = new ShopifyOrderFetcher(api());

        assertEquals("shipped", fetcher.toRow(order("fulfilled", "paid", null)).get("orderStatus"));
        assertEquals("processing", fetcher.toRow(order(null, "paid", null)).get("orderStatus"));
        assertEquals("pending", fetcher.toRow(order(null, "authorized", null)).get("orderStatus"));
        assertEquals("cancelled", fetcher.toRow(order(null, "refunded", null)).get("orderStatus"));
        assertEquals("cancelled",
                fetcher.toRow(order(null, "paid", "2026-01-25T00:00:00-04:00")).get("orderStatus"));
    }

    // ---------- 商品字段桥接 ----------

    @Test
    void shouldExplodeOneRowPerVariantWithProductFieldsRepeated() {
        List<Map<String, Object>> rows = enqueueProduct(PRODUCT_FIXTURE).fetch(productRequest(null), credential);

        assertEquals(2, rows.size(), "价格/SKU/重量都是变体级属性，必须按变体出行");
        assertEquals("2001", rows.get(0).get("external_id"));
        assertEquals("Classic Tee", rows.get(0).get("title"));
        assertEquals("TEE-S-BLK", rows.get(0).get("sku"));
        assertEquals("3001", rows.get(0).get("variant_id"));
        assertEquals("19.90", rows.get(0).get("price"));
        assertEquals(Boolean.TRUE, rows.get(0).get("active"));
    }

    @Test
    void shouldConvertVariantWeightIntoGrams() {
        List<Map<String, Object>> rows = enqueueProduct(PRODUCT_FIXTURE).fetch(productRequest(null), credential);

        assertEquals("200", rows.get(0).get("weight"), "0.2kg 必须写成 200 克");
        assertEquals("226.796", rows.get(1).get("weight"), "8oz 必须换算成克");
    }

    @Test
    void shouldJoinVariantOptionsIntoAttributePairs() {
        List<Map<String, Object>> rows = enqueueProduct(PRODUCT_FIXTURE).fetch(productRequest(null), credential);

        assertEquals("Size:S,Color:Black", rows.get(0).get("attributes"));
    }

    @Test
    void shouldDropEmptySkuSoTheImporterRaisesRequiredFieldError() {
        List<Map<String, Object>> rows = enqueueProduct(PRODUCT_FIXTURE).fetch(productRequest(null), credential);

        assertFalse(rows.get(1).containsKey("sku"), "缺 SKU 的行要让导入侧报必填校验，而不是存空串");
    }

    @Test
    void shouldFilterByKeywordOnTitleAndSku() {
        List<Map<String, Object>> rows = enqueueProduct(PRODUCT_FIXTURE).fetch(productRequest("TEE-S"), credential);

        assertEquals(1, rows.size());
        assertEquals("TEE-S-BLK", rows.get(0).get("sku"));
    }

    @Test
    void shouldFlattenHtmlDescriptionToPlainText() {
        assertEquals("Soft cotton & breathable",
                ShopifyProductFetcher.plainText("<p>Soft cotton &amp; <br>breathable</p>"));
        assertNull(ShopifyProductFetcher.plainText("<p>  </p>"));
    }

    // ---------- 发货回写 ----------

    @Test
    void shouldPushOnlyFulfillableLineItemsWithTrackingInfo() {
        enqueue(200, """
                {"order":{"id":1001,"line_items":[
                  {"id":5001,"fulfillable_quantity":2},{"id":5002,"fulfillable_quantity":0},
                  {"id":5003,"fulfillable_quantity":1}]}}""");
        queue.add(new Scripted(200, """
                {"fulfillment":{"id":8800,"status":"pending","created_at":"2026-03-22T03:04:05-04:00"}}""", null));

        PlatformFulfillmentResult result = new ShopifyFulfillmentPusher(api()).push(
                new PlatformFulfillmentRequest("#1001", "SF1234567890", "SF Express",
                        "https://example.com/track/SF1234567890", true), credential);

        assertEquals(2, recorded.size());
        assertEquals("GET", recorded.get(0).method);
        assertEquals("/admin/api/2026-01/orders/1001.json", recorded.get(0).path);
        assertEquals("POST", recorded.get(1).method);
        assertEquals("/admin/api/2026-01/orders/1001/fulfillments.json", recorded.get(1).path);
        String sent = sentBodies.get(1);
        assertTrue(sent.contains("\"line_items_by_id\":[\"5001\",\"5003\"]"),
                "fulfillable_quantity=0 的行不能被再次履行: " + sent);
        assertTrue(sent.contains("\"number\":\"SF1234567890\""), sent);
        assertTrue(sent.contains("\"company\":\"SF Express\""), sent);
        assertTrue(sent.contains("\"url\":\"https://example.com/track/SF1234567890\""), sent);
        assertTrue(sent.contains("\"notify_customer\":true"), sent);
        assertEquals("8800", result.platformFulfillmentId());
        assertEquals("pending", result.status());
        assertEquals(2, result.lineItemCount());
        assertEquals("2026-03-22T07:04:05", String.valueOf(result.fulfilledAt()), "平台时间要归一到 UTC");
    }

    @Test
    void shouldRefuseToPushWhenNothingIsFulfillable() {
        enqueue(200, """
                {"order":{"id":1001,"line_items":[{"id":5001,"fulfillable_quantity":0}]}}""");

        PlatformFulfillmentException error = assertThrows(PlatformFulfillmentException.class,
                () -> new ShopifyFulfillmentPusher(api()).push(
                        new PlatformFulfillmentRequest("1001", "SF123", "SF", null, false), credential));

        assertEquals(1, recorded.size(), "判定没有可履行行之后必须收手，不能发出 POST");
        assertTrue(error.getMessage().contains("已经回写过"), error.getMessage());
    }

    @Test
    void shouldRejectNonNumericOrderIdBeforeTouchingTheUrlPath() {
        // 订单号会被拼进请求路径，含 / 或 ? 的值必须拒掉，否则等于给对端拼 URL
        BusinessException error = assertThrows(BusinessException.class,
                () -> new ShopifyFulfillmentPusher(api()).push(
                        new PlatformFulfillmentRequest("1001/../other", "SF123", "SF", null, false), credential));

        assertEquals("SHOPIFY_ORDER_ID_INVALID", error.getErrorCode());
        assertTrue(recorded.isEmpty(), "非法订单号不能发出任何请求");
    }

    @Test
    void shouldRequireTrackingNumber() {
        assertThrows(PlatformFulfillmentException.class,
                () -> new ShopifyFulfillmentPusher(api()).push(
                        new PlatformFulfillmentRequest("1001", " ", "SF", null, false), credential));

        assertTrue(recorded.isEmpty());
    }

    // ---------- 连通性测试 ----------

    @Test
    void shouldReportShopIdentityWithoutLeakingTheToken() {
        enqueue(200, """
                {"shop":{"name":"Demo Store","myshopify_domain":"demo.myshopify.com","currency":"USD"}}""");

        PlatformConnectionTestResult result = new ShopifyConnectionTester(api()).test(credential);

        assertTrue(result.success(), result.message());
        assertTrue(result.message().contains("Demo Store"), result.message());
        assertTrue(result.message().contains("2026-01"), result.message());
        assertFalse(result.message().contains("shpat_test"), "回显信息不得包含令牌");
        assertEquals(PlatformAuthType.SHOPIFY.name(), result.adapterType());
    }

    @Test
    void shouldReturnFailureReasonInsteadOfThrowing() {
        enqueue(401, "{\"errors\":\"[API] Invalid API key\"}");

        PlatformConnectionTestResult result = new ShopifyConnectionTester(api()).test(credential);

        assertFalse(result.success());
        assertTrue(result.message().contains("accessToken"), result.message());
    }

    // ---------- 测试装置 ----------

    /**
     * 排入一条脚本响应并返回可直接调用平台订单接口的适配器。
     */
    private ShopifyOrderFetcher enqueue(int status, String body) {
        queue.add(new Scripted(status, body, null));
        return new ShopifyOrderFetcher(api());
    }

    /**
     * 同上，返回商品适配器。
     */
    private ShopifyProductFetcher enqueueProduct(String body) {
        queue.add(new Scripted(200, body, null));
        return new ShopifyProductFetcher(api());
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPIFY", null, null, null,
                null, null, null, limit, 1);
    }

    private PlatformProductFetchRequest productRequest(String keyword) {
        return new PlatformProductFetchRequest("tenant-1", "SHOPIFY", storeUrl, "ak", "", keyword, 50, 1);
    }

    private ShopifyAdminApi api() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new ShopifyAdminApi(RestClient.builder(), factory, "2026-01", 20, 1, Duration.ofMillis(10));
    }

    private static Map<String, Object> order(String fulfillmentStatus, String financialStatus, String cancelledAt) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 1001);
        if (fulfillmentStatus != null) {
            order.put("fulfillment_status", fulfillmentStatus);
        }
        if (financialStatus != null) {
            order.put("financial_status", financialStatus);
        }
        if (cancelledAt != null) {
            order.put("cancelled_at", cancelledAt);
        }
        return order;
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        recorded.add(entry);
        sentBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        Scripted next = queue.isEmpty() ? new Scripted(200, "{\"orders\":[]}", null) : queue.poll();
        byte[] payload = next.body.getBytes(StandardCharsets.UTF_8);
        if (next.linkHeader != null) {
            exchange.getResponseHeaders().add("Link", next.linkHeader);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(next.status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class Recorded {
        private String method;
        private String path;
    }

    private record Scripted(int status, String body, String linkHeader) {
    }
}
