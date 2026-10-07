package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shopee 传输层（{@link ShopeeOpenApi}）的请求形状契约测试，经订单适配器真实外呼到本地 {@link HttpServer}。
 * <p>
 * 断言的是「我们怎么调 Shopee」：公共参数进 query（含可复算的 {@code sign}）、业务参数进 JSON body、
 * {@code x-env-type} 按 region 出现/省略、签名串里没有凭据进不了日志、15 天窗口被就地截断、游标被跟随、
 * 明细调用显式带 {@code response_optional_fields}、信封 {@code error} 非空即失败。<b>响应体字段名未真实验证</b>
 * （无沙箱凭据，归 ISS-065），故 fixture 只用于验证探测式取值与请求形状。
 *
 * @author kenyon
 */
class ShopeeOpenApiContractTest {

    private static final String PARTNER_ID = "pid_1";
    private static final String PARTNER_KEY = "pkey_secret";
    private static final String ACCESS_TOKEN = "atk_live";
    private static final String SHOP_ID = "shop_9";

    private static final String LIST_OK = """
            {"request_id":"r1","error":"","message":"","warning":"","response":{"order_list":[],"more":false}}""";
    private static final String DETAIL_OK = """
            {"request_id":"r2","error":"","message":"","response":{"orders":[]}}""";

    private HttpServer server;
    private String storeUrl;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<String> listScripted = new ArrayDeque<>();
    private final Deque<String> detailScripted = new ArrayDeque<>();

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

    @Test
    void shouldPutPublicParamsInQueryStringIncludingSign() {
        listScripted.add(LIST_OK);

        fetcher().fetch(orderRequest(LocalDateTime.parse("2026-01-25T00:00:00"),
                LocalDateTime.parse("2026-03-22T00:00:00")), credential(null));

        Recorded list = firstList();
        Map<String, String> query = parseQuery(list.query);
        assertEquals(PARTNER_ID, query.get("partner_id"));
        assertEquals(ACCESS_TOKEN, query.get("access_token"));
        assertEquals(SHOP_ID, query.get("shop_id"));
        assertTrue(query.containsKey("timestamp"), "§6：公共参数带 timestamp: " + query);
        assertTrue(query.containsKey("sign"), "§6：公共参数带 sign: " + query);
    }

    @Test
    void shouldSignWithPartnerIdPathTimestampTokenShopOrderOverTheSameTimestampSent() {
        listScripted.add(LIST_OK);

        fetcher().fetch(orderRequest(LocalDateTime.parse("2026-01-25T00:00:00"),
                LocalDateTime.parse("2026-03-22T00:00:00")), credential(null));

        Recorded list = firstList();
        Map<String, String> query = parseQuery(list.query);
        long timestamp = Long.parseLong(query.get("timestamp"));
        String expected = ShopeeSignature.sign(PARTNER_KEY, ShopeeSignature.baseString(
                PARTNER_ID, list.path, timestamp, query.get("access_token"), query.get("shop_id")));

        assertEquals(expected, query.get("sign"), "sign 必须能按 §6 口径复算，否则每次调用都会 error_signature");
    }

    @Test
    void shouldAddEnvHeaderOnlyWhenRegionIsAValidEnvironment() {
        listScripted.add(LIST_OK);
        listScripted.add(LIST_OK);

        fetcher().fetch(orderRequest(), credential("sandbox"));
        assertEquals("sandbox", firstList().header(ShopeeOpenApi.ENV_HEADER), "§6：region=sandbox 切沙箱");

        recorded.clear();
        fetcher().fetch(orderRequest(), credential(null));
        assertFalse(firstList().headers.containsKey(ShopeeOpenApi.ENV_HEADER.toLowerCase()),
                "region 空白时省略 x-env-type，不臆造取值");
    }

    @Test
    void shouldNeverUseCustomShopApiKeyHeaders() {
        listScripted.add(LIST_OK);

        fetcher().fetch(orderRequest(), credential(null));

        Recorded list = firstList();
        assertFalse(list.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 约定，不该出现在 Shopee");
        assertFalse(list.headers.containsKey("x-api-secret"));
        assertFalse(list.headers.containsKey("authorization"), "Shopee 用签名认证，没有 Bearer 头");
    }

    @Test
    void shouldClampTimeRangeTo15DaysAndLogNotSilentlyLoopWindows() {
        listScripted.add(LIST_OK);

        // 跨度 30 天，超过 §6 的 15 天硬上限
        fetcher().fetch(orderRequest(LocalDateTime.parse("2026-01-25T00:00:00"),
                LocalDateTime.parse("2026-10-31T00:00:00")), credential(null));

        Map<String, Object> body = PlatformJson.object(firstList().body);
        long from = ((Number) body.get("time_from")).longValue();
        long to = ((Number) body.get("time_to")).longValue();
        assertEquals(15L * 24 * 3600, to - from, "time_to 应被夹到 time_from + 15 天");
        assertEquals(1, recorded.stream().filter(r -> r.path.endsWith("get_order_list")).count(),
                "截断而不是在一次调用里循环多个窗口");
    }

    @Test
    void shouldSelectUpdateTimeFieldWhenIncrementalAndPutBusinessParamsInBody() {
        listScripted.add(LIST_OK);

        fetcher().fetch(orderRequest(LocalDateTime.parse("2026-01-25T00:00:00"),
                LocalDateTime.parse("2026-03-22T00:00:00")), credential(null));

        Recorded list = firstList();
        Map<String, Object> body = PlatformJson.object(list.body);
        assertEquals("update_time", body.get("time_range_field"));
        // 业务参数在 body 不在 query（§6）
        assertFalse(parseQuery(list.query).containsKey("time_from"));
        assertTrue(body.containsKey("time_from") && body.containsKey("time_to") && body.containsKey("page_size"));
    }

    @Test
    void shouldFollowCursorReturnedByPreviousPage() {
        listScripted.add("""
                {"error":"","response":{"order_list":[{"order_sn":"SN1"}],"more":true,"next_cursor":"C2"}}""");
        listScripted.add("""
                {"error":"","response":{"order_list":[{"order_sn":"SN2"}],"more":false}}""");
        detailScripted.add(DETAIL_OK);
        detailScripted.add(DETAIL_OK);

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(), credential(null));

        List<Recorded> lists = listCalls();
        assertEquals(2, lists.size(), "more=true 时跟随 next_cursor 再翻一页");
        Map<String, Object> secondBody = PlatformJson.object(lists.get(1).body);
        assertEquals("C2", secondBody.get("cursor"), "下一页游标来自响应 next_cursor");
        assertEquals(2, rows.size());
    }

    @Test
    void shouldStopPagingWhenCursorDoesNotAdvance() {
        for (int i = 0; i < 10; i++) {
            listScripted.add("""
                    {"error":"","response":{"order_list":[{"order_sn":"SN1"}],"more":true,"next_cursor":"SAME"}}""");
            detailScripted.add(DETAIL_OK);
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(orderRequest(), credential(null));

        assertEquals(2, listCalls().size(), "next_cursor 与本页所用游标相同(不推进)时收手，不能空转到页数上限 20");
        assertEquals(1, rows.size(), "重复页里的同一单不能重复出行");
    }

    @Test
    void shouldRequestDetailWithExplicitOptionalFieldsAndOrderSnList() {
        listScripted.add("""
                {"error":"","response":{"order_list":[{"order_sn":"SN1","order_status":"TO_SHIP"}],"more":false}}""");
        detailScripted.add("""
                {"error":"","response":{"orders":[{"order_sn":"SN1","shipping":{"name":"Bob"}}]}}""");

        fetcher().fetch(orderRequest(), credential(null));

        Recorded detail = firstDetail();
        Map<String, Object> body = PlatformJson.object(detail.body);
        assertEquals("shipping,buyer,invoice,item_list", body.get("response_optional_fields"),
                "§6：不显式列这些字段，收件地址根本不返回");
        assertTrue(String.valueOf(body.get("order_sn_list")).contains("SN1"), "§6：明细按 order_sn_list[] 回取");
    }

    @Test
    void shouldFailWithPlatformMessageWhenEnvelopeErrorIsNonEmpty() {
        listScripted.add("""
                {"request_id":"r","error":"error_permission","message":"shop not authorized"}""");

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(), credential(null)));

        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertTrue(error.getMessage().contains("error_permission"), error.getMessage());
        assertTrue(error.getMessage().contains("shop not authorized"), error.getMessage());
        assertFalse(error.getMessage().contains(ACCESS_TOKEN), "异常消息不得回显令牌: " + error.getMessage());
    }

    @Test
    void shouldGuardMissingCredentialFieldsWithoutEchoingValues() {
        PlatformCredential noShop = new PlatformCredential(PlatformAuthType.SHOPEE, "tenant-1", "SHOPEE",
                storeUrl, PARTNER_ID, PARTNER_KEY, ACCESS_TOKEN, null, null, "rt", null, null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(), noShop));

        assertTrue(error.getMessage().contains("externalShopId"), error.getMessage());
        assertFalse(error.getMessage().contains(ACCESS_TOKEN), error.getMessage());
        assertTrue(recorded.isEmpty(), "参数不全就不该发出请求");
    }

    @Test
    void shouldRequireCredentialForSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class, () -> fetcher().fetch(orderRequest()));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private ShopeeOrderFetcher fetcher() {
        return fetcher(20);
    }

    private ShopeeOrderFetcher fetcher(int maxPages) {
        return new ShopeeOrderFetcher(api(maxPages));
    }

    private ShopeeOpenApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new ShopeeOpenApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential(String region) {
        return new PlatformCredential(PlatformAuthType.SHOPEE, "tenant-1", "SHOPEE", storeUrl,
                PARTNER_ID, PARTNER_KEY, ACCESS_TOKEN, null, null, "rt_test", region, SHOP_ID, null);
    }

    private static PlatformOrderFetchRequest orderRequest() {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPEE", null, null, null,
                null, null, null, 50, 1);
    }

    private static PlatformOrderFetchRequest orderRequest(LocalDateTime from, LocalDateTime to) {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPEE", null, null, null,
                null, from, to, 50, 1);
    }

    private Recorded firstList() {
        return recorded.stream().filter(r -> r.path.endsWith("get_order_list")).findFirst().orElseThrow();
    }

    private Recorded firstDetail() {
        return recorded.stream().filter(r -> r.path.endsWith("get_order_detail")).findFirst().orElseThrow();
    }

    private List<Recorded> listCalls() {
        return recorded.stream().filter(r -> r.path.endsWith("get_order_list")).toList();
    }

    private static Map<String, String> parseQuery(String rawUri) {
        Map<String, String> map = new LinkedHashMap<>();
        int index = rawUri.indexOf('?');
        if (index < 0) {
            return map;
        }
        for (String pair : rawUri.substring(index + 1).split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                map.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return map;
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = path;
        entry.query = exchange.getRequestURI().toString();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        boolean isList = path.endsWith("get_order_list");
        Deque<String> queue = isList ? listScripted : detailScripted;
        String body = queue.isEmpty() ? (isList ? LIST_OK : DETAIL_OK) : queue.poll();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class Recorded {
        private String method;
        private String path;
        private String query;
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }
}
