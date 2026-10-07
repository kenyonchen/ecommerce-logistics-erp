package site.hexaarch.ecommerce.logistics.infrastructure.platform.aliexpress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderImportTargetField;
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
 * {@link AliexpressOrderFetcher} 的调用形状、翻页与字段桥接契约测试。
 * <p>
 * 断言「我们怎么调 {@code aliexpress.trade.seller.orderlist.get}」：§5.2 的 {@code param_aeop_order_query} 里
 * <b>必填、从 1 起</b>的 {@code current_page}/{@code page_size} 总在、增量走 {@code modified_date_*} 否则
 * {@code create_date_*}、{@code total_page} 驱动第二页、成功信封 {@code aliexpress_trade_seller_orderlist_get_response}
 * 被解包、以及凭据只进表单而<b>不发 X-API-Key</b>（那是 CUSTOM_SHOP 约定）。
 * <b>响应订单对象字段名未经真实平台核实</b>（无沙箱凭据，归 ISS-065），fixture 只用于验证探测式取值与中立行键对齐。
 *
 * @author kenyon
 */
class AliexpressOrderFetcherContractTest {

    private static final String APP_KEY = "1234567";
    private static final String APP_SECRET = "top_app_secret_test";
    private static final String SESSION = "sess_5001_token";

    /**
     * §5.1 的成功信封形态：方法名 snake 化 + {@code _response}，内含 §5.2 的 {@code total_page} 与（未核实的）订单数组
     */
    private static final String PAGE1_FIXTURE = """
            {"aliexpress_trade_seller_orderlist_get_response":{"total_page":2,"current_page":1,"orders":[{
              "orderId":"8001234567890","buyerFullName":"Ana Souza","email":"ana@example.com",
              "orderStatus":"WAIT_SELLER_SEND_GOODS","gmtCreate":"2026-01-25 08:15:00","currency":"USD",
              "totalAmount":"129.90","paymentType":"MIXEDCARD","paymentStatus":"PAID",
              "receiverCountry":"BR","receiverState":"SP","receiverCity":"São Paulo",
              "receiverAddress":"Rua Exemplo 100","receiverPostCode":"01000-000","receiverPhone":"+5511999"
            }]}}""";

    private static final String PAGE2_FIXTURE = """
            {"aliexpress_trade_seller_orderlist_get_response":{"total_page":2,"current_page":2,"orders":[{
              "orderId":"8009876543210","orderStatus":"SELLER_SEND_GOODS"
            }]}}""";

    private static final String EMPTY_FIXTURE = """
            {"aliexpress_trade_seller_orderlist_get_response":{"total_page":1,"orders":[]}}""";

    private HttpServer server;
    private String storeUrl;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<String> scripted = new ArrayDeque<>();

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
    void shouldAlwaysSendOneBasedCurrentPageAndPageSizeInsideParamObject() {
        scripted.add(EMPTY_FIXTURE);

        fetcher().fetch(orderRequest(50), credential());

        Map<String, Object> query = queryObject(recorded.get(0));
        assertEquals(1, ((Number) query.get("current_page")).intValue(), "§5.2：current_page 从 1 起");
        assertTrue(query.containsKey("page_size"), "§5.2：page_size 必填: " + query);
        // 没有时间界时兜底一个 create_date 窗口
        assertTrue(query.containsKey("create_date_start") && query.containsKey("create_date_end"),
                "两个界都没有时给回溯窗口: " + query);
        assertFalse(query.containsKey("modified_date_start"), "非增量不发 modified_date_*: " + query);
    }

    @Test
    void shouldUseModifiedDateWindowWhenIncrementalBoundsPresent() {
        scripted.add(EMPTY_FIXTURE);

        fetcher().fetch(new PlatformOrderFetchRequest("t", "ALIEXPRESS", null, null, null,
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"), 50, 1),
                credential());

        Map<String, Object> query = queryObject(recorded.get(0));
        assertTrue(query.containsKey("modified_date_start") && query.containsKey("modified_date_end"),
                "增量应走 modified_date_*: " + query);
        assertFalse(query.containsKey("create_date_start"), "增量应走 modified_date_*: " + query);
    }

    @Test
    void shouldFollowTotalPageIntoASecondRequestIncrementingCurrentPage() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(50), credential());

        assertEquals(2, recorded.size(), "total_page=2 时应跟一页");
        Map<String, Object> first = queryObject(recorded.get(0));
        Map<String, Object> second = queryObject(recorded.get(1));
        assertEquals(1, ((Number) first.get("current_page")).intValue());
        assertEquals(2, ((Number) second.get("current_page")).intValue(), "续页 current_page 递增");
        assertEquals(2, rows.size());
    }

    @Test
    void shouldUnwrapTheSnakeCasedSuccessEnvelopeAndBridgeNeutralRowKeys() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(EMPTY_FIXTURE);

        Map<String, Object> row = fetcher().fetch(orderRequest(50), credential()).get(0);

        assertEquals("8001234567890", row.get("orderId"), "从 aliexpress_trade_seller_orderlist_get_response 解出的订单");
        assertEquals("Ana Souza", row.get("customerName"));
        assertEquals("ana@example.com", row.get("email"));
        assertEquals("processing", row.get("orderStatus"), "WAIT_SELLER_SEND_GOODS 是已付款待发货");
        assertEquals("2026-01-25 08:15:00", row.get("orderTime"), "gmtCreate 已是导入侧格式，原样透出");
        assertEquals("USD", row.get("currency"));
        assertEquals("129.90", row.get("totalAmount"));
        assertEquals("MIXEDCARD", row.get("paymentMethod"));
        assertEquals("PAID", row.get("paymentStatus"));
        assertEquals("Ana Souza", row.get("recipientName"), "无收件地址块时收件人退到买家名");
        assertEquals("+5511999", row.get("phoneNumber"));
        assertEquals("BR", row.get("country"));
        assertEquals("SP", row.get("province"));
        assertEquals("São Paulo", row.get("city"));
        assertEquals("Rua Exemplo 100", row.get("addressLine1"));
        assertEquals("01000-000", row.get("zipCode"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(EMPTY_FIXTURE);

        Map<String, Object> row = fetcher().fetch(orderRequest(50), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("buyerFullName"), "不得把平台原始键直接抛给导入侧");
    }

    @Test
    void shouldStopWhenThePlatformIgnoresCurrentPageAndRepeatsTheSameIds() {
        for (int i = 0; i < 10; i++) {
            scripted.add(PAGE1_FIXTURE);
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(orderRequest(1000), credential());

        assertEquals(2, recorded.size(), "同一页 id 被重复返回时收手，不在上限 20 上空转");
        assertEquals(1, rows.size(), "重复页里的同一单不能重复出行");
    }

    @Test
    void shouldNeverSendCustomShopApiKeyHeadersBecauseCredentialsTravelInTheForm() {
        scripted.add(EMPTY_FIXTURE);

        fetcher().fetch(orderRequest(50), credential());

        Recorded sent = recorded.get(0);
        assertFalse(sent.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 约定，TOP 不该发");
        assertFalse(sent.headers.containsKey("x-api-secret"));
        assertFalse(sent.headers.containsKey("authorization"));
        String body = decode(sent.body);
        assertTrue(body.contains("session=" + SESSION) && body.contains("app_key=" + APP_KEY),
                "凭据以表单公共参数携带: " + body);
        assertFalse(body.contains(APP_SECRET), "app_secret 只参与签名，绝不进 body: " + body);
    }

    @Test
    void shouldBridgeStatusWithoutInventingUnseenValues() {
        assertEquals("pending", AliexpressOrderFetcher.bridgeStatus("WAIT_BUYER_PAY"));
        assertEquals("processing", AliexpressOrderFetcher.bridgeStatus("WAIT_SELLER_SEND_GOODS"));
        assertEquals("shipped", AliexpressOrderFetcher.bridgeStatus("SELLER_SEND_GOODS"),
                "与 WAIT_SELLER_SEND_GOODS 只差前缀，必须整串匹配");
        assertEquals("completed", AliexpressOrderFetcher.bridgeStatus("ORDER_FINISHED"));
        assertEquals("cancelled", AliexpressOrderFetcher.bridgeStatus("IN_CANCEL"));
        assertEquals("SomethingNew", AliexpressOrderFetcher.bridgeStatus("SomethingNew"), "认不出要原样透出");
    }

    @Test
    void shouldRequireCredentialForSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class, () -> fetcher().fetch(orderRequest(5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private AliexpressOrderFetcher fetcher() {
        return fetcher(20);
    }

    private AliexpressOrderFetcher fetcher(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        AliexpressTopClient client = new AliexpressTopClient(
                new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)), "md5", maxPages);
        return new AliexpressOrderFetcher(client);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.ALIEXPRESS, "tenant-1", "ALIEXPRESS", storeUrl,
                APP_KEY, APP_SECRET, SESSION, null, null, null, null, null, null);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "ALIEXPRESS", null, null, null,
                null, null, null, limit, 1);
    }

    /**
     * 从被发送的表单里取 {@code param_aeop_order_query} 的 JSON 对象。逐对拆分后再 URL 解码，
     * 避免整串解码后值里的 {@code &} 被误当参数分隔符。
     */
    private static Map<String, Object> queryObject(Recorded sent) {
        for (String pair : sent.body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if ("param_aeop_order_query".equals(key)) {
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                return PlatformJson.object(value);
            }
        }
        throw new IllegalStateException("表单缺少 param_aeop_order_query: " + decode(sent.body));
    }

    private static String decode(String rawBody) {
        return URLDecoder.decode(rawBody, StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        String body = scripted.isEmpty() ? EMPTY_FIXTURE : scripted.poll();
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
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();
    }
}
