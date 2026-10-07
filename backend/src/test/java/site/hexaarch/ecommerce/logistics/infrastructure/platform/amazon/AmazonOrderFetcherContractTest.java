package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * Amazon SP-API 订单适配器的查询形状、翻页与字段桥接契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，断言「我们怎么调用 {@code /orders/v0/orders}」
 * （§3.3 的 PascalCase 参数、二选一必填的时间界、1-100 的单页夹取、{@code NextToken} 翻页）
 * 与「我们怎么解析返回的订单」。<b>响应体字段名未经真实平台验证</b>（当前无沙箱凭据，归 ISS-065），
 * 因此这里的 fixture 只用于验证探测式取值与中立行键的对齐，不能当作平台侧结构的证据。
 *
 * @author kenyon
 */
class AmazonOrderFetcherContractTest {

    private static final String ACCESS_TOKEN = "atk_test";
    private static final String MARKETPLACE_US = "ATVPDKIKX0DER";

    /**
     * v0 列表响应的文档形态：订单对象带 PascalCase 字段与嵌套 {@code ShippingAddress}，翻页游标在 {@code payload} 里
     */
    private static final String PAGE1_FIXTURE = """
            {"payload":{"Orders":[{
              "AmazonOrderId":"113-5189979-2577705",
              "BuyerName":"Jane Doe",
              "BuyerEmail":"jane@example.com",
              "PurchaseDate":"2026-01-25T08:30:00Z",
              "LastUpdateDate":"2026-03-22T09:00:00Z",
              "Currency":"USD",
              "OrderTotal":{"Amount":"129.90","CurrencyCode":"USD"},
              "Status":"Unshipped",
              "PaymentMethod":"Visa",
              "PaymentExecutionStatus":"NotYetConfirmed",
              "ShippingAddress":{"Name":"Jane Doe","Phone":"206-555-0100","CountryCode":"US",
                                 "StateOrRegion":"WA","City":"Seattle","DistrictOrNeighborhood":"Ballard",
                                 "AddressLine1":"400 Broad St","AddressLine2":"Apt 5","PostalCode":"98101"},
              "OrderItems":{"Item":[
                {"OrderItemId":"1","ASIN":"B0EXAMPLE001","SellerSku":"TEE-S-BLK","Title":"Classic Tee",
                 "QuantityOrdered":2,"ItemPrice":{"Amount":"24.50","CurrencyCode":"USD"}}]}
            }],"NextToken":"PAGE2_TOKEN"}}""";

    private static final String PAGE2_FIXTURE = """
            {"payload":{"Orders":[{
              "AmazonOrderId":"114-7654321-1234567",
              "BuyerName":"Sam Roe",
              "PurchaseDate":"2026-01-25T11:15:00Z",
              "Currency":"USD",
              "OrderTotal":{"Amount":"19.99","CurrencyCode":"USD"},
              "Status":"Shipped"
            }]}}""";

    private HttpServer server;
    private String storeUrl;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<Scripted> scripted = new ArrayDeque<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.setExecutor(null);
        server.start();
        storeUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------- 查询参数形状 ----------

    @Test
    void shouldSendLastUpdatedAfterAndOmitCreatedAfterForIncrementalSync() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "AMAZON", null, null, null,
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"),
                50, 1), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("LastUpdatedAfter=2026-01-25T00:00:00Z"),
                "§3.3：增量界存在时按平台侧更新时间取: " + query);
        assertFalse(query.contains("CreatedAfter="), "CreatedAfter 与 LastUpdatedAfter 二选一，不能同时发: " + query);
        assertTrue(query.contains("MarketplaceIds=" + MARKETPLACE_US), query);
    }

    @Test
    void shouldFallBackToAThirtyDayCreatedAfterWindowWhenNoBoundExists() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(orderRequest(50), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("CreatedAfter="), "两个时间界都没有时仍必须带其中一个: " + query);
        assertFalse(query.contains("LastUpdatedAfter="), query);
        LocalDateTime sent = PlatformJson.toUtcLocalDateTime(queryParam(query, "CreatedAfter"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(!sent.isAfter(now.minusDays(29)) && !sent.isBefore(now.minusDays(31)),
                "回溯窗口应是 30 天，实测 " + sent + " 与 " + now + " 相差过大");
    }

    @Test
    void shouldClampPageSizeToOneHundredAndSendNoNextTokenOnFirstPage() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(orderRequest(500), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("MaxResultsPerPage=100"), "§3.3：单页上限 100，超出的必须压回来: " + query);
        assertFalse(query.contains("NextToken="), "首页不能带游标: " + query);
    }

    @Test
    void shouldFollowNextTokenKeepingEarlierParamsOnContinuationPages() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(50), credential());

        assertEquals(2, recorded.size());
        assertEquals(2, rows.size(), "两页共 1 + 1 条");
        assertFalse(decode(recorded.get(0).query).contains("NextToken="), recorded.get(0).query);
        String second = decode(recorded.get(1).query);
        assertTrue(second.contains("NextToken=PAGE2_TOKEN"), second);
        assertTrue(second.contains("MarketplaceIds=" + MARKETPLACE_US), "续页要保留首页的必填参数: " + second);
        assertTrue(second.contains("CreatedAfter="), second);
    }

    @Test
    void shouldStopPagingWhenThePlatformRepeatsTheSameToken() {
        for (int i = 0; i < 10; i++) {
            scripted.add(new Scripted(200, PAGE1_FIXTURE));
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(orderRequest(1000), credential());

        assertEquals(2, recorded.size(), "同一个游标被重复返回时最多再跟一页就收手，不能翻满页数上限");
        assertEquals(1, rows.size(), "重复页里的同一订单不能重复出行");
    }

    @Test
    void shouldStopPagingAtConfiguredMaxPages() {
        int distinctPages = 6;
        for (int i = 0; i < distinctPages; i++) {
            scripted.add(new Scripted(200, PAGE1_FIXTURE.replace("PAGE2_TOKEN", "TOKEN_" + i)));
        }

        fetcher(3).fetch(orderRequest(1000), credential());

        assertEquals(3, recorded.size(), "页数上限是防失控的保险丝，到点必须停");
    }

    @Test
    void shouldFilterByOrderNumberAfterFetchingSinceV0HasNoIdParameter() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "AMAZON",
                null, null, null, "114-7654321-1234567", null, null, 50, 1), credential());

        assertEquals(1, rows.size());
        assertEquals("114-7654321-1234567", rows.get(0).get("orderId"));
    }

    @Test
    void shouldBackOffAndRetryOnRateLimit() {
        scripted.add(new Scripted(429, "{\"errors\":[{\"message\":\"Too many requests\"}]}"));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(5), credential());

        assertEquals(2, recorded.size(), "429 属于暂时性失败，应按退避重试");
        assertEquals(1, rows.size());
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutWastingQuota() {
        scripted.add(new Scripted(401, "{\"errors\":[{\"message\":\"The access token doesn't or no longer exists\"}]}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(5), credential()));

        assertEquals(1, recorded.size(), "令牌错误重试没有意义，只会白耗 0.0167 rps 的配额");
        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains(ACCESS_TOKEN), "异常消息不得回显令牌: " + error.getMessage());
    }

    // ---------- 字段桥接 ----------

    @Test
    void shouldBridgeOrderFieldsIntoNeutralRowKeys() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertEquals("113-5189979-2577705", row.get("orderId"));
        assertEquals("Jane Doe", row.get("customerName"));
        assertEquals("jane@example.com", row.get("email"));
        assertEquals("processing", row.get("orderStatus"), "Unshipped 是已付款待发货，不能按未处理归类");
        // v0 的 PurchaseDate 是 UTC 的 Z 结尾形态，导入侧只认 yyyy-MM-dd HH:mm:ss
        assertEquals("2026-01-25 08:30:00", row.get("orderTime"));
        assertEquals("USD", row.get("currency"));
        assertEquals("129.90", row.get("totalAmount"));
        assertEquals("Visa", row.get("paymentMethod"));
        assertEquals("NotYetConfirmed", row.get("paymentStatus"));
        assertEquals("Jane Doe", row.get("recipientName"));
        assertEquals("206-555-0100", row.get("phoneNumber"));
        assertEquals("US", row.get("country"));
        assertEquals("WA", row.get("province"));
        assertEquals("Seattle", row.get("city"));
        assertEquals("400 Broad St", row.get("addressLine1"));
        assertEquals("98101", row.get("zipCode"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("ShippingAddress"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("OrderItems"));
    }

    @Test
    void shouldPassLineItemArrayThroughWithoutRewritingPlatformKeys() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) fetcher().fetch(orderRequest(10), credential()).get(0).get("lineItems");

        assertEquals(1, items.size());
        assertEquals("TEE-S-BLK", items.get(0).get("SellerSku"),
                "明细数组的键名与元素结构都未被 §3.3 核实，原样透传以便实测后一次性对齐");
        assertEquals(2, items.get(0).get("QuantityOrdered"));
    }

    @Test
    void shouldTolerateAnOrderWithoutAddressOrItemsInsteadOfFailingTheBatch() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertEquals("114-7654321-1234567", row.get("orderId"));
        assertEquals("shipped", row.get("orderStatus"));
        assertEquals("Sam Roe", row.get("recipientName"), "没有收件地址时收件人退到买家名，其余地址键留空");
        assertNull(row.get("addressLine1"));
        assertNull(row.get("zipCode"));
        assertEquals(List.of(), row.get("lineItems"), "缺明细要出行但不抛异常，由导入侧的必填校验去报这一行");
    }

    @Test
    void shouldReadOrdersWhenTheEnvelopeCarriesThePayloadArrayDirectly() {
        scripted.add(new Scripted(200, """
                {"payload":[{"AmazonOrderId":"112-1111111-1111111","Status":"Pending"}],"nextToken":null}"""));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(10), credential());

        assertEquals(1, rows.size(), "payload 直接是数组也是一种已知形态，不能只认一种");
        assertEquals("112-1111111-1111111", rows.get(0).get("orderId"));
        assertEquals("pending", rows.get(0).get("orderStatus"));
    }

    @Test
    void shouldBridgeStatusWithoutInventingUnseenValues() {
        assertEquals("cancelled", AmazonOrderFetcher.bridgeStatus("OrderCancelled"));
        assertEquals("cancelled", AmazonOrderFetcher.bridgeStatus("PurchaseOrderWasCancelled"));
        assertEquals("processing", AmazonOrderFetcher.bridgeStatus("Unshipped"));
        assertEquals("shipped", AmazonOrderFetcher.bridgeStatus("PartiallyShipped"));
        assertEquals("shipped", AmazonOrderFetcher.bridgeStatus("Shipped"));
        assertEquals("pending", AmazonOrderFetcher.bridgeStatus("Pending"));
        assertEquals("pending", AmazonOrderFetcher.bridgeStatus("PendingAvailability"));
        assertEquals("SomethingNew", AmazonOrderFetcher.bridgeStatus("SomethingNew"), "认不出要原样透出");
        assertNull(AmazonOrderFetcher.bridgeStatus("  "));
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 连通性测试 ----------

    @Test
    void shouldProbeWithTheCheapestAuthenticatedReadAndHideTheToken() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        PlatformConnectionTestResult result = new AmazonConnectionTester(api(1)).test(credential());

        assertTrue(result.success(), result.message());
        assertEquals(PlatformAuthType.AMAZON_SP_API.name(), result.adapterType());
        assertTrue(result.message().contains("NA"), result.message());
        assertFalse(result.message().contains(ACCESS_TOKEN), "回显信息不得包含令牌: " + result.message());
        assertEquals("GET", recorded.get(0).method);
        assertEquals("/orders/v0/orders", recorded.get(0).path);
        assertEquals(ACCESS_TOKEN, recorded.get(0).header(AmazonSpApi.ACCESS_TOKEN_HEADER));
        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("MaxResultsPerPage=1"), "最小代价: " + query);
        assertTrue(query.contains("CreatedAfter="), "v0 必须带时间界: " + query);
    }

    @Test
    void shouldReturnFailureReasonInsteadOfThrowing() {
        scripted.add(new Scripted(403, "{\"errors\":[{\"message\":\"Unauthorized\"}]}"));

        PlatformConnectionTestResult result = new AmazonConnectionTester(api(1)).test(credential());

        assertFalse(result.success());
        assertTrue(result.message().contains("凭据"), result.message());
        assertFalse(result.message().contains(ACCESS_TOKEN), result.message());
    }

    @Test
    void shouldReportMissingMarketplaceIdsAsFailureInsteadOfThrowing() {
        PlatformCredential noMarketplace = new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1",
                "AMAZON", storeUrl, "ak", "sk", ACCESS_TOKEN, null, null, "rt", "EU", null, null);

        PlatformConnectionTestResult result = new AmazonConnectionTester(api(1)).test(noMarketplace);

        assertFalse(result.success());
        assertTrue(result.message().contains("MarketplaceIds"), result.message());
        assertTrue(recorded.isEmpty(), "参数不全就不该发出请求");
    }

    // ---------- 测试装置 ----------

    private AmazonOrderFetcher fetcher() {
        return fetcher(20);
    }

    private AmazonOrderFetcher fetcher(int maxPages) {
        return new AmazonOrderFetcher(api(maxPages));
    }

    private AmazonSpApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：要验证 429 退避路径，但契约测试不能真的睡很久
        return new AmazonSpApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1", "AMAZON", storeUrl,
                "amzn1.client_id_test", "client_secret_test", ACCESS_TOKEN, null, null, "rt_test_refresh",
                "NA", null, MARKETPLACE_US);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "AMAZON", null, null, null,
                null, null, null, limit, 1);
    }

    private static String decode(String rawUriWithQuery) {
        return URLDecoder.decode(rawUriWithQuery, StandardCharsets.UTF_8);
    }

    private static String queryParam(String decodedQuery, String name) {
        String queryString = decodedQuery.substring(decodedQuery.indexOf('?') + 1);
        for (String pair : queryString.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && name.equals(pair.substring(0, separator))) {
                return pair.substring(separator + 1);
            }
        }
        throw new IllegalStateException("查询参数 " + name + " 不存在: " + decodedQuery);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().toString();
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, "{\"payload\":{\"Orders\":[]}}") : scripted.poll();
        byte[] payload = next.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(next.status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class Recorded {
        private String method;
        private String path;
        private String query;
        private final Map<String, String> headers = new LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Scripted(int status, String body) {
    }
}
