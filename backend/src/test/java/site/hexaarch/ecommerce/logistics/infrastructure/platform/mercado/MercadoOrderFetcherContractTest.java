package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

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
 * Mercado Libre CBT 订单适配器的查询形状、翻页与字段桥接契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，断言的是 docs/spec/platform-api-reference.md §7 <b>已核实</b>的那部分：
 * {@code GET /marketplace/orders/search} 的 {@code seller.id}/{@code limit}/{@code offset}/
 * {@code last_updated.gte|lt} 参数名、时间界是<b>带 UTC 偏移的 ISO-8601 字符串</b>、
 * {@code Authorization: Bearer} 鉴权、{@code {results,paging}} 信封，以及 {@code offset} 随页大小推进
 * 并在 {@code paging.total} 取满或平台不推进时收手。
 * <p>
 * <b>订单对象的字段名未经真实平台验证</b>（§7 只核实了端点、参数与信封，没有核实订单结构与逐单明细端点；
 * 当前也无沙箱凭据，归 ISS-065），所以 fixture 里的 {@code id}/{@code order_items}/{@code total_amount}/
 * {@code currency_id}/{@code paid_time}/{@code status}/{@code buyer} 等键只用于验证「候选键探测 → 中立行键」
 * 这条通路，不能当作平台侧结构的证据；收件地址类字段则按实现约定恒为空。
 *
 * @author kenyon
 */
class MercadoOrderFetcherContractTest {

    private static final String ACCESS_TOKEN = "atk_test";
    private static final String REFRESH_TOKEN = "rt_test_one_time";
    private static final String CLIENT_ID = "1357924680";
    private static final String CLIENT_SECRET = "test_client_secret";
    private static final String SELLER_USER_ID = "987654321";
    private static final String FIRST_ORDER_ID = "1234567890";
    private static final String SECOND_ORDER_ID = "1234567891";

    /**
     * CBT 搜索的已知信封形态：{@code results[]} + {@code paging{total,offset,limit}}；
     * 订单对象的字段名全部是候选探测（§7 未核实），这里刻意带上 {@code seller} 以验证它不外泄
     */
    private static final String PAGE1_FIXTURE = """
            {"results":[{
              "id":1234567890,
              "status":"ready_to_ship",
              "shipping_status":"ready_to_ship",
              "date_created":"2026-01-25T08:00:00.000-03:00",
              "paid_time":"2026-01-25T11:30:00.000+00:00",
              "total_amount":99.8,
              "currency_id":"BRL",
              "payment_method":"account_money",
              "payment_status":"approved",
              "buyer":{"nickname":"JANE DOE","email":"jane@example.com"},
              "seller":{"id":987654321},
              "order_items":[{"item_id":"MLB111","quantity":2,"unit_price":49.9,"currency_id":"BRL"}]
            }],"paging":{"total":150,"offset":0,"limit":100}}""";

    /**
     * 第二页：只有单号/状态/时间探得到，其余留空——正是「明细缺失要被导入侧必填校验拦下」的那种行
     */
    private static final String PAGE2_FIXTURE = """
            {"results":[{
              "id":1234567891,
              "status":"delivered",
              "date_created":"2026-03-22T10:00:00.000+00:00",
              "seller":{"id":987654321}
            }],"paging":{"total":150,"offset":100,"limit":100}}""";

    private static final String EMPTY_FIXTURE = "{\"results\":[],\"paging\":{\"total\":0,\"offset\":0,\"limit\":100}}";

    /**
     * §7 实测的错误信封（连通性测试要把它摊开回显给运维）
     */
    private static final String INVALID_CLIENT_FIXTURE =
            "{\"message\":\"invalid client_id or client_secret\",\"error\":\"invalid_client\",\"status\":400,\"cause\":[]}";

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

    // ---------- 鉴权头与 host ----------

    @Test
    void shouldAuthenticateWithBearerHeaderAndNoCustomShopOrOzonHeaders() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(100, null, null), credential());

        Recorded request = recorded.get(0);
        assertEquals("GET", request.method);
        assertEquals("/marketplace/orders/search", request.path, "§7：CBT 跨境订单搜索端点");
        assertEquals("Bearer " + ACCESS_TOKEN, request.header("authorization"));
        assertFalse(request.headers.containsKey("x-api-key"), "不得沿用自建店铺的 X-API-Key 约定");
        assertFalse(request.headers.containsKey("x-api-secret"));
        assertFalse(request.headers.containsKey("client-id"), "Client-Id 是 Ozon 的形状，与 Mercado 无关");
        assertFalse(request.headers.containsKey("api-key"));
    }

    @Test
    void shouldUseVerifiedMarketplaceHostWhenStoreUrlIsBlank() {
        String uri = api(1).ordersUri(credential(null, SELLER_USER_ID), Map.of()).toString();

        assertTrue(uri.startsWith("https://api.mercadolibre.com/marketplace/orders/search"),
                "§7：storeUrl 留空时用核实到的官方 host: " + uri);
    }

    @Test
    void shouldFailBeforeAnyRequestWhenAccessTokenMissing() {
        PlatformCredential tokenless = new PlatformCredential(PlatformAuthType.MERCADO_LIBRE, "tenant-1",
                "MERCADO", storeUrl, CLIENT_ID, CLIENT_SECRET, null, null, null, REFRESH_TOKEN,
                null, SELLER_USER_ID, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(5, null, null), tokenless));

        assertEquals("PLATFORM_ACCESS_TOKEN_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("apiKey"), error.getMessage());
        assertTrue(error.getMessage().contains("apiSecret"), error.getMessage());
        assertTrue(error.getMessage().contains("refreshToken"), error.getMessage());
        assertFalse(error.getMessage().contains(CLIENT_SECRET), "异常消息不得回显凭据值: " + error.getMessage());
        assertTrue(recorded.isEmpty(), "令牌缺失说明令牌交换没跑过，不能发出注定 401 的请求白耗配额");
    }

    // ---------- seller.id 与时间界 ----------

    @Test
    void shouldSourceSellerIdFromExternalShopId() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(100, null, null), credential());

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("seller.id=" + SELLER_USER_ID),
                "§7：seller.id 只能来自集成的 externalShopId(卖家 user_id): " + query);
        assertTrue(query.contains("limit=100"), query);
        assertTrue(query.contains("offset=0"), "首页的 offset 必须是 0: " + query);
    }

    @Test
    void shouldRequireExternalShopIdAndNameTheField() {
        MercadoOrderFetcher fetcher = fetcher();

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher.fetch(orderRequest(10, null, null), credential(storeUrl, null)));

        assertEquals("PLATFORM_EXTERNAL_SHOP_ID_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("externalShopId"), error.getMessage());
        assertTrue(error.getMessage().contains("seller.id"), "要点名平台侧参数,运维才知道是哪一栏: " + error.getMessage());
        assertTrue(recorded.isEmpty(), "不知道查哪个卖家就不该发出请求");
    }

    @Test
    void shouldSendTimeBoundsAsIsoTimestampsWithUtcOffset() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(100,
                LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00")), credential());

        String raw = recorded.get(0).query;
        assertTrue(raw.contains("%2B00%3A00"),
                "偏移量的 + 必须被百分号编码，否则接收端按表单口径会把它读成空格: " + raw);
        String query = decode(raw);
        assertTrue(query.contains("last_updated.gte=2026-01-25T00:00:00+00:00"),
                "§7：时间界是带偏移的 ISO-8601 字符串: " + query);
        assertTrue(query.contains("last_updated.lt=2026-03-22T00:00:00+00:00"), query);
        assertFalse(query.contains("date_created="), "增量同步按平台侧更新时间取，不另发创建时间界: " + query);
    }

    @Test
    void shouldFallBackToAThirtyDayWindowWhenNoBoundExists() {
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        fetcher().fetch(orderRequest(100, null, null), credential());

        String query = decode(recorded.get(0).query);
        String gte = queryParam(query, "last_updated.gte");
        String lt = queryParam(query, "last_updated.lt");
        assertTrue(gte.endsWith("+00:00") && lt.endsWith("+00:00"), "补出来的默认窗口也要带偏移: " + query);
        LocalDateTime sent = PlatformJson.toUtcLocalDateTime(gte);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(!sent.isAfter(now.minusDays(29)) && !sent.isBefore(now.minusDays(31)),
                "回溯窗口应是 30 天，实测 " + sent + " 与 " + now + " 相差过大");
    }

    // ---------- 翻页 ----------

    @Test
    void shouldAdvanceOffsetByPageSizeAndStopAtPagingTotal() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(100, null, null), credential());

        assertEquals(2, recorded.size(), "paging.total=150 > 单页 100，要再取一页");
        assertEquals(2, rows.size(), "两页共 1 + 1 条");
        assertEquals(0, Integer.parseInt(queryParam(decode(recorded.get(0).query), "offset")));
        String second = decode(recorded.get(1).query);
        assertTrue(second.contains("offset=100"), "offset 按单页条数推进: " + second);
        assertTrue(second.contains("last_updated.gte="), "续页要保留首页的过滤条件: " + second);
    }

    @Test
    void shouldStopAfterTheFirstPageWhenPagingTotalFitsThePage() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE.replace("\"total\":150", "\"total\":1")));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(100, null, null), credential());

        assertEquals(1, recorded.size(), "paging.total 已取满就不再翻页，白耗 429 配额");
        assertEquals(1, rows.size());
    }

    @Test
    void shouldStopPagingOnEmptyResults() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));
        scripted.add(new Scripted(200, EMPTY_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(100, null, null), credential());

        assertEquals(2, recorded.size());
        assertEquals(1, rows.size(), "results 为空即到底，不再跟着 offset 空转");
    }

    @Test
    void shouldStopAndDedupeWhenThePageDoesNotAdvance() {
        // 平台忽略 offset、两页返回同一批订单：第二页全是重复行，必须收手而不是翻满页数上限
        for (int i = 0; i < 8; i++) {
            scripted.add(new Scripted(200, PAGE1_FIXTURE));
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(orderRequest(100, null, null), credential());

        assertEquals(2, recorded.size(), "整页重复说明 offset 没被遵守，最多再试一页就停");
        assertEquals(1, rows.size(), "重复页里的同一订单不能重复出行，否则导入侧按幂等键互相覆盖");
    }

    @Test
    void shouldStopPagingAtConfiguredMaxPages() {
        for (int i = 0; i < 10; i++) {
            // 每页给一个新单号、并把 paging.total 抬高，绕开重复检测与「取满即停」，验证页数保险丝本身
            scripted.add(new Scripted(200, distinctPage(i)));
        }

        fetcher(3).fetch(orderRequest(100, null, null), credential());

        assertEquals(3, recorded.size(), "页数上限是防失控的保险丝，到点必须停");
    }

    @Test
    void shouldFilterByOrderNumberAfterFetchingSinceSearchHasNoIdParameter() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "MERCADO",
                null, null, null, SECOND_ORDER_ID, null, null, 100, 1), credential());

        assertEquals(1, rows.size());
        assertEquals(SECOND_ORDER_ID, rows.get(0).get("orderId"));
    }

    // ---------- 字段桥接 ----------

    @Test
    void shouldBridgeOrderFieldsIntoNeutralRowKeys() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(100, null, null), credential()).get(0);

        assertEquals(FIRST_ORDER_ID, row.get("orderId"), "数字 id 要按字符串出行（导入侧的幂等键口径）");
        assertEquals("JANE DOE", row.get("customerName"));
        assertEquals("jane@example.com", row.get("email"));
        assertEquals("processing", row.get("orderStatus"), "ready_to_ship 是已收款待发货");
        // paid_time 优先于 date_created：下单时间按支付时刻取，两者都带偏移，换算成 UTC 墙上时间
        assertEquals("2026-01-25 11:30:00", row.get("orderTime"));
        assertEquals("BRL", row.get("currency"));
        assertEquals("99.8", row.get("totalAmount"), "金额要保留平台给的小数位");
        assertEquals("account_money", row.get("paymentMethod"));
        assertEquals("approved", row.get("paymentStatus"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(100, null, null), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("buyer"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("seller"));
        assertFalse(row.containsKey("order_items"));
    }

    @Test
    void shouldPassLineItemArrayThroughWithoutRewritingPlatformKeys() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) fetcher().fetch(orderRequest(100, null, null), credential()).get(0).get("lineItems");

        assertEquals(1, items.size());
        assertEquals("MLB111", items.get(0).get("item_id"),
                "明细数组的键名与元素结构都未被 §7 核实，原样透传以便实测后一次性对齐");
        assertEquals(2, items.get(0).get("quantity"));
    }

    @Test
    void shouldTolerateAnOrderWithoutBuyerOrItemsInsteadOfFailingTheBatch() {
        scripted.add(new Scripted(200, PAGE2_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(100, null, null), credential()).get(0);

        assertEquals(SECOND_ORDER_ID, row.get("orderId"));
        assertEquals("completed", row.get("orderStatus"));
        assertEquals("2026-03-22 10:00:00", row.get("orderTime"), "没有 paid_time 时退到 date_created");
        assertNull(row.get("customerName"));
        assertNull(row.get("totalAmount"));
        assertNull(row.get("recipientName"), "收件地址在 §7 里只有货件端点能给，订单搜索行不探测");
        assertEquals(List.of(), row.get("lineItems"), "缺明细要出行但不抛异常，由导入侧的必填校验去报这一行");
    }

    @Test
    void shouldTreatABodyWithoutResultsArrayAsNoOrders() {
        scripted.add(new Scripted(200, "{\"paging\":{\"total\":0}}"));

        assertEquals(List.of(), fetcher().fetch(orderRequest(100, null, null), credential()));
        assertEquals(1, recorded.size());
    }

    @Test
    void shouldBridgeStatusWithoutInventingUnseenValues() {
        assertEquals("pending", MercadoOrderFetcher.bridgeStatus("pending"));
        assertEquals("processing", MercadoOrderFetcher.bridgeStatus("ready_to_ship"));
        assertEquals("processing", MercadoOrderFetcher.bridgeStatus("READY-TO-SHIP"));
        assertEquals("shipped", MercadoOrderFetcher.bridgeStatus("shipped"));
        assertEquals("completed", MercadoOrderFetcher.bridgeStatus("delivered"));
        assertEquals("cancelled", MercadoOrderFetcher.bridgeStatus("cancelled"));
        assertEquals("paid", MercadoOrderFetcher.bridgeStatus("paid"), "订单侧的未核实取值要原样透出");
        assertNull(MercadoOrderFetcher.bridgeStatus("  "));
        assertNull(MercadoOrderFetcher.bridgeStatus(null));
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(5, null, null)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 连通性测试 ----------

    @Test
    void shouldProbeWithTheCheapestAuthenticatedReadAndHideTheToken() {
        scripted.add(new Scripted(200, PAGE1_FIXTURE));

        PlatformConnectionTestResult result = new MercadoConnectionTester(api(1)).test(credential());

        assertTrue(result.success(), result.message());
        assertEquals(PlatformAuthType.MERCADO_LIBRE.name(), result.adapterType());
        Recorded request = recorded.get(0);
        assertEquals("GET", request.method);
        assertEquals("/marketplace/orders/search", request.path);
        assertEquals("Bearer " + ACCESS_TOKEN, request.header("authorization"));
        String query = decode(request.query);
        assertTrue(query.contains("seller.id=" + SELLER_USER_ID), query);
        assertTrue(query.contains("limit=1"), "最小代价: " + query);
        assertFalse(query.contains("last_updated"), "连通性不需要时间窗: " + query);
        assertTrue(result.message().contains(SELLER_USER_ID), "seller.id 是非敏感标识，可以回显: " + result.message());
        assertFalse(result.message().contains(ACCESS_TOKEN), "回显信息不得包含令牌: " + result.message());
    }

    @Test
    void shouldReturnFailureReasonWithTheMeasuredErrorEnvelope() {
        scripted.add(new Scripted(400, INVALID_CLIENT_FIXTURE));

        PlatformConnectionTestResult result = new MercadoConnectionTester(api(1)).test(credential());

        assertFalse(result.success());
        assertTrue(result.message().contains("invalid_client"), result.message());
        assertTrue(result.message().contains("invalid client_id or client_secret"), result.message());
        assertTrue(result.message().contains("apiKey"), "要指出这是客户端凭据问题: " + result.message());
        assertFalse(result.message().contains(CLIENT_SECRET), result.message());
        assertFalse(result.message().contains(ACCESS_TOKEN), result.message());
    }

    @Test
    void shouldReportMissingSellerIdAsFailureWithoutCalling() {
        PlatformConnectionTestResult result = new MercadoConnectionTester(api(1)).test(credential(storeUrl, null));

        assertFalse(result.success());
        assertTrue(result.message().contains("externalShopId"), result.message());
        assertTrue(recorded.isEmpty(), "参数不全就不该发出请求");
    }

    // ---------- 测试装置 ----------

    private MercadoOrderFetcher fetcher() {
        return fetcher(20);
    }

    private MercadoOrderFetcher fetcher(int maxPages) {
        return new MercadoOrderFetcher(api(maxPages));
    }

    private MercadoApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：要验证 429 退避分支，但契约测试不能真的睡很久
        return new MercadoApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)), maxPages);
    }

    private PlatformCredential credential() {
        return credential(storeUrl, SELLER_USER_ID);
    }

    private PlatformCredential credential(String storeUrl, String externalShopId) {
        return new PlatformCredential(PlatformAuthType.MERCADO_LIBRE, "tenant-1", "MERCADO", storeUrl,
                CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, null, null, REFRESH_TOKEN, null, externalShopId, null);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit, LocalDateTime updatedFrom, LocalDateTime updatedTo) {
        return new PlatformOrderFetchRequest("tenant-1", "MERCADO", null, null, null,
                null, updatedFrom, updatedTo, limit, 1);
    }

    /**
     * 每页换一个单号、并把 paging.total 抬到 1000：既绕开「整页重复」也绕开「取满即停」，
     * 让 {@link #shouldStopPagingAtConfiguredMaxPages()} 只剩页数保险丝这一道闸可验
     */
    private static String distinctPage(int index) {
        return PAGE1_FIXTURE
                .replace("\"id\":" + FIRST_ORDER_ID, "\"id\":" + (2_000_000_000L + index))
                .replace("\"total\":150", "\"total\":1000");
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
