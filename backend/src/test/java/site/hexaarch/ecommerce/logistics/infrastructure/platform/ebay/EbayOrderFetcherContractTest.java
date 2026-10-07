package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * eBay Sell 订单适配器的查询形状、翻页与字段桥接契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，核心断言是「我们怎么调用 {@code /sell/fulfillment/v1/order}」这一层协议事实：
 * Bearer 鉴权（不是 {@code X-API-Key}）、只发 spec 的五个参数、走单个 {@code filter} 增量窗口、
 * {@code limit} 夹到 200、跟随响应体 {@code next} 且不自己算 {@code offset}、对 next 做 host 归属校验。
 * <b>这是本次交付最要紧的回归护栏</b>：断言 {@code getAllOrders}/{@code order_status=}/{@code creation_date_range=}
 * 一个都不出现（§0 推翻预设之一）。<b>响应体字段名未经真实平台验证</b>（无沙箱凭据，归 ISS-065），
 * 这里的 fixture 只用于验证探测式取值与中立行键对齐，不能当作平台侧结构证据。
 *
 * @author kenyon
 */
class EbayOrderFetcherContractTest {

    private static final String BEARER = "v__ebay_oauth_token_test";

    /**
     * 一单完整、一单缺字段，外加一个把游标指回下一页的 {@code next}（与配置同 host，应被整体跟随）。
     */
    private static final String PAGE1_TEMPLATE = """
            {"href":"__BASE__/sell/fulfillment/v1/order?limit=2",
             "next":"__BASE__/sell/fulfillment/v1/order?limit=2&offset=2",
             "order":[{
               "orderId":"01-11111-11111",
               "orderStatus":"AWAITING_FULFILLMENT",
               "createdTime":"2026-01-25T08:30:00.000Z",
               "lastUpdatedTime":"2026-03-22T09:00:00.000Z",
               "buyer":{"username":"jane_b","email":"jane@example.com"},
               "totalAmount":{"currency":"USD","value":"129.90"},
               "amountPaid":{"currency":"USD","value":"129.90"},
               "paymentSummary":{"status":"PAID","paymentMethod":"PAYPAL"},
               "shipTo":{"primaryContact":{"name":"Jane Doe","phone":"206-555-0100"},
                         "address":{"country":"US","stateOrProvince":"WA","city":"Seattle",
                                    "addressLine1":"400 Broad St","postalCode":"98101"}},
               "orderLineItems":[{"lineItem":{"id":"01-11111-11111-1"},"quantityPurchased":2}]
             }]}""";

    /**
     * 末页：没有 {@code next}，且订单缺 shipTo/明细，用于验证容忍缺字段。
     */
    private static final String PAGE2_FIXTURE = """
            {"order":[{"orderId":"01-22222-22222","orderStatus":"FULFILLED",
                      "createdTime":"2026-01-25T11:15:00.000Z",
                      "totalAmount":{"currency":"USD","value":"19.99"}}]}""";

    private static final String EMPTY_FIXTURE = "{\"order\":[]}";

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

    // ---------- 鉴权头 ----------

    @Test
    void shouldAuthenticateWithBearerTokenAndNeverUseXApiKey() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(5), credential());

        Recorded request = recorded.get(0);
        assertEquals("GET", request.method);
        assertEquals("/sell/fulfillment/v1/order", request.path);
        assertEquals("Bearer " + BEARER, request.header("authorization"));
        assertFalse(request.headers.containsKey("x-api-key"), "§4.1：eBay 用 Bearer，不沿用自建店铺的 X-API-Key 约定");
        assertFalse(request.headers.containsKey("x-api-secret"));
    }

    @Test
    void shouldFailBeforeAnyRequestWhenAccessTokenMissing() {
        PlatformCredential tokenless = new PlatformCredential(PlatformAuthType.EBAY_SELL, "tenant-1", "EBAY",
                storeUrl, "appid", "secret", null, null, null, "rt", null, null, "EBAY_US");

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(5), tokenless));

        assertEquals("PLATFORM_ACCESS_TOKEN_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty(), "令牌缺失说明令牌交换没跑过，不能发出注定 401 的请求");
    }

    // ---------- 查询参数形状（本次最要紧的回归） ----------

    @Test
    void shouldFilterByLastmodifieddateAndOmitEverySupersededParamName() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "EBAY", null, null, null,
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"),
                50, 1), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("filter=lastmodifieddate:[2026-01-25T00:00:00Z..2026-03-22T00:00:00Z]"),
                "§4.2：增量窗口一律走单个 filter，两字点分隔: " + query);
        assertFalse(query.contains("order_status="), "§0：order_status= 独立参数已废弃，发出去等于发不存在的参数: " + query);
        assertFalse(query.contains("creation_date_range="), "§0：creation_date_range= 独立参数已废弃: " + query);
        assertFalse(query.contains("modification_date_range="), "§0：modification_date_range= 独立参数已废弃: " + query);
        assertFalse(recorded.get(0).path.contains("getAllOrders"), "§0：getAllOrders 端点在当前 spec 已不存在");
        assertFalse(query.contains("marketplaceId="), "§4.2：GET /order 的参数里没有 marketplaceId");
    }

    @Test
    void shouldFallBackToAThirtyDayTrailingWindowWhenNoBoundExists() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(orderRequest(50), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("filter=lastmodifieddate:["), query);
        String window = query.substring(query.indexOf("lastmodifieddate:[") + "lastmodifieddate:[".length());
        window = window.substring(0, window.indexOf(']'));
        String from = window.split("\\.\\.")[0];
        LocalDateTime sent = site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson
                .toUtcLocalDateTime(from);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        assertTrue(!sent.isAfter(now.minusDays(29)) && !sent.isBefore(now.minusDays(31)),
                "两个界都没有时应回退到 30 天回溯窗口，实测 " + sent + " 与 " + now + " 相差过大");
    }

    @Test
    void shouldClampLimitToTwoHundred() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(500), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("limit=200"), "§4.2：limit 上限 200，>200 平台报错，超出的必须压回来: " + query);
        assertFalse(query.contains("offset="), "首页不能自己算 offset: " + query);
    }

    @Test
    void shouldFilterExactOrderByOrderIdsWhichIsASpecParam() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "EBAY",
                null, null, null, "01-22222-22222", null, null, 50, 1), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("orderIds=01-22222-22222"), "§4.2：orderIds 是 spec 的合法参数，精确取单用它: " + query);
        assertFalse(query.contains("filter="), "精确取单不再叠加时间窗 filter: " + query);
        assertEquals(1, rows.size());
        assertEquals("01-22222-22222", rows.get(0).get("orderId"));
    }

    // ---------- 翻页 ----------

    @Test
    void shouldFollowResponseNextWithoutComputingOffset() {
        scripted.add(new Scripted(200, template(PAGE1_TEMPLATE)));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(50), credential());

        assertEquals(2, recorded.size());
        assertEquals(2, rows.size(), "两页共 1 + 1 条");
        assertFalse(decode(recorded.get(0).query).contains("offset="), "首页由我们拼，只有 filter/limit: " + recorded.get(0).query);
        String second = decode(recorded.get(1).query);
        assertTrue(second.contains("offset=2"), "续页整体跟随响应体 next（offset 由 eBay 算）: " + second);
    }

    @Test
    void shouldRepinNextFromForeignHostOntoTheConfiguredBase() {
        // next 指向生产 api.ebay.com，但当前 base 是本地测试 host：必须只取 path+query 重新钉到本地，绝不外发
        scripted.add(new Scripted(200, """
                {"next":"https://api.ebay.com/sell/fulfillment/v1/order?limit=2&offset=2",
                 "order":[{"orderId":"01-33333-33333"}]}"""));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        fetcher().fetch(orderRequest(50), credential());

        assertEquals(2, recorded.size(), "被拒绝盲发的外部 URL 应改写成本地 base 后续跑，而不是失败");
        Recorded second = recorded.get(1);
        assertEquals("/sell/fulfillment/v1/order", second.path);
        assertTrue(decode(second.query).contains("offset=2"), second.query);
        // origin-form 请求行的 getRequestURI() 没有 authority，落到本地只能看 Host 头
        String host = second.header("host");
        assertTrue(host != null && host.endsWith("127.0.0.1:" + server.getAddress().getPort()),
                "续页 host 必须是配置的 base 而不是 api.ebay.com: " + host);
    }

    @Test
    void shouldStopPagingWhenNextDoesNotAdvance() {
        String stuckUrl = storeUrl + "/sell/fulfillment/v1/order?orderIds=STUCK&limit=200";
        scripted.add(new Scripted(200,
                "{\"next\":\"" + stuckUrl + "\",\"order\":[{\"orderId\":\"STUCK\"}]}"));
        scripted.add(new Scripted(200, PAGE1_TEMPLATE));

        List<Map<String, Object>> rows = fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "EBAY",
                null, null, null, "STUCK", null, null, 200, 1), credential());

        assertEquals(1, recorded.size(), "next 指回刚请求过的 URL = 游标不推进，收手而不是翻满页数上限");
        assertEquals(1, rows.size());
    }

    // ---------- 字段桥接 ----------

    @Test
    void shouldBridgeOrderFieldsIntoNeutralRowKeys() {
        scripted.add(new Scripted(200, template(PAGE1_TEMPLATE)));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertEquals("01-11111-11111", row.get("orderId"));
        assertEquals("jane_b", row.get("customerName"));
        assertEquals("jane@example.com", row.get("email"));
        assertEquals("pending", row.get("orderStatus"), "AWAITING_FULFILLMENT 是待处理，不能按已发货归类");
        assertEquals("2026-01-25 08:30:00", row.get("orderTime"));
        assertEquals("USD", row.get("currency"));
        assertEquals("129.90", row.get("totalAmount"));
        assertEquals("PAYPAL", row.get("paymentMethod"));
        assertEquals("PAID", row.get("paymentStatus"));
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
        scripted.add(new Scripted(200, template(PAGE1_TEMPLATE)));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("shipTo"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("orderLineItems"));
    }

    @Test
    void shouldTolerateAnOrderWithoutShipToOrItemsInsteadOfFailingTheBatch() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(10), credential()).get(0);

        assertEquals("01-22222-22222", row.get("orderId"));
        assertEquals("shipped", row.get("orderStatus"));
        assertNull(row.get("recipientName"));
        assertNull(row.get("addressLine1"));
        assertEquals(List.of(), row.get("lineItems"), "缺明细要出行但不抛异常，由导入侧的必填校验去报这一行");
    }

    @Test
    void shouldBridgeStatusWithoutInventingUnseenValues() {
        assertEquals("cancelled", EbayOrderFetcher.bridgeStatus("ORDER_CANCELLED"));
        assertEquals("pending", EbayOrderFetcher.bridgeStatus("AWAITING_PAYMENT"));
        assertEquals("pending", EbayOrderFetcher.bridgeStatus("AWAITING_FULFILLMENT"), "含 fulfill 但要先按待处理归类");
        assertEquals("shipped", EbayOrderFetcher.bridgeStatus("FULFILLED"));
        assertEquals("processing", EbayOrderFetcher.bridgeStatus("PARTIALLY_PAID"));
        assertEquals("SomethingNew", EbayOrderFetcher.bridgeStatus("SomethingNew"), "认不出要原样透出");
        assertNull(EbayOrderFetcher.bridgeStatus("  "));
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class, () -> fetcher().fetch(orderRequest(5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 传输层 base / marketplaceId 判定 ----------

    @Test
    void shouldUseVerifiedProductionHostWhenStoreUrlIsBlank() {
        assertEquals("https://apiz.ebay.com", api(1).ordersUri(credential(null, null), Map.of()).toString()
                .substring(0, "https://apiz.ebay.com".length()));
    }

    @Test
    void shouldResolveMarketplaceIdButNeverFromTheOrdersList() {
        assertEquals("EBAY_US", api(1).marketplaceId(credential()));
        // marketplaceId 助手存在但订单列表不发它——由 shouldFilterByLastmodifieddateAndOmitEverySupersededParamName 保证
        assertNull(api(1).marketplaceId(credential(null, null)));
    }

    // ---------- 连通性测试 ----------

    @Test
    void shouldProbeWithTheCheapestAuthenticatedReadAndHideTheToken() {
        scripted.add(new Scripted(200, PAGE1_TEMPLATE));

        PlatformConnectionTestResult result = new EbayConnectionTester(api(1)).test(credential());

        assertTrue(result.success(), result.message());
        assertEquals(PlatformAuthType.EBAY_SELL.name(), result.adapterType());
        assertFalse(result.message().contains(BEARER), "回显信息不得包含令牌: " + result.message());
        assertEquals("GET", recorded.get(0).method);
        assertEquals("/sell/fulfillment/v1/order", recorded.get(0).path);
        assertEquals("Bearer " + BEARER, recorded.get(0).header("authorization"));
        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("limit=1"), "最小代价: " + query);
        assertFalse(query.contains("filter="), "最小探测不带时间窗: " + query);
    }

    @Test
    void shouldReturnFailureReasonInsteadOfThrowingAndWithoutLeakingToken() {
        scripted.add(new Scripted(403, "{\"error\":\"Unauthorized\"}"));

        PlatformConnectionTestResult result = new EbayConnectionTester(api(1)).test(credential());

        assertFalse(result.success());
        assertFalse(result.message().contains(BEARER), result.message());
    }

    // ---------- 测试装置 ----------

    private EbayOrderFetcher fetcher() {
        return new EbayOrderFetcher(api(20));
    }

    private EbaySellApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：契约测试要验证退避但不真睡
        return new EbaySellApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return credential(storeUrl, "EBAY_US");
    }

    private PlatformCredential credential(String storeUrl, String marketplaceIds) {
        return new PlatformCredential(PlatformAuthType.EBAY_SELL, "tenant-1", "EBAY", storeUrl,
                "appid", "secret", BEARER, null, null, "rt", null, null, marketplaceIds);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "EBAY", null, null, null,
                null, null, null, limit, 1);
    }

    private String template(String withPlaceholder) {
        return withPlaceholder.replace("__BASE__", storeUrl);
    }

    private static String decode(String rawUriWithQuery) {
        return URLDecoder.decode(rawUriWithQuery, StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().toString();
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, EMPTY_FIXTURE) : scripted.poll();
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
