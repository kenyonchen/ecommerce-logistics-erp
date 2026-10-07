package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentResult;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Magento 发货回写的契约测试：两步链（先读订单再 {@code ship}）与发货体的键集合。
 * <p>
 * §9（docs/spec/platform-api-reference.md）核实到的形状都在这里逐条断言：
 * <ol>
 *   <li>端点是<b>单数</b> {@code POST /rest/<store>/V1/order/<orderId>/ship}（订单列表是复数 {@code orders}，
 *       写混了就 404），所以路径逐字比对；</li>
 *   <li>发货体只有 §9 点名的 {@code items[{order_item_id, qty}]}、{@code notify}、
 *       {@code tracks[{track_number, title}]}，键集合按字面断言而不是「差不多就行」；
 *       ERP 的 {@code notifyCustomer} 直接映射 {@code notify}；</li>
 *   <li>§9 只核实了发货体要求逐行的 {@code order_item_id}，而端口只给订单号 ⇒ 必须先读一次订单，
 *       读的是 §9 <b>已核实</b>的列表端点加 {@code entity_id} 的 {@code eq} 过滤（不是未核实的
 *       {@code /V1/orders/<id>}），并且要确认读回来的确实是请求的那一单。</li>
 * </ol>
 * {@code tracks[].carrier_code} 与跟踪链接<b>不发</b>（§9 只给了键名没给取值口径，拿承运商名称去填
 * 店铺注册的承运商代码会被判非法参数），这一条也按字面断言，将来要发就是补常量而不是改形状。
 * <p>
 * 订单行与发货响应里的字段名（{@code item_id}/{@code qty_ordered}/{@code entity_id}）§9 未核实
 * （ISS-065），fixture 只验证候选探测与失败口径，不能作为平台侧结构的证据。
 *
 * @author kenyon
 */
class MagentoFulfillmentPusherContractTest {

    private static final String PASTED_TOKEN = "pasted-integration-token-never-echoed";
    private static final String ADMIN_USER = "erp_sync";
    private static final String ADMIN_PASSWORD = "admin-pass-never-echoed";
    private static final String ISSUED_TOKEN = "asdf3hjklp5iuytre";
    private static final String TRACKING = "SF1234567890";
    private static final String FILTER_TRIPLE = "searchCriteria[filter_groups][0][filters][0]";

    /**
     * 列表端点 + {@code entity_id eq} 读回来的订单：两条行项目，键名是候选探测的其中一种可能
     */
    private static final String ORDER_WITH_TWO_LINES = """
            {"items":[{
              "entity_id":7,"status":"processing",
              "items":[{"item_id":71,"sku":"TEE-BLK","qty_ordered":2},
                       {"item_id":72,"sku":"MUG","quantity_ordered":1}]
            }],"search_criteria":[],"total_count":1}""";

    private static final String EMPTY_ORDERS = "{\"items\":[],\"total_count\":0}";

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

    // ---------- 两步链 ----------

    @Test
    void shouldReadTheOrderThroughTheVerifiedListEndpointBeforeShipping() {
        scripted.add(new Scripted(200, ORDER_WITH_TWO_LINES));
        scripted.add(new Scripted(200, "{\"entity_id\":9001,\"status\":\"open\"}"));

        pusher().push(request(true), credential());

        assertEquals(List.of("/rest/default/V1/orders", "/rest/default/V1/order/7/ship"),
                recorded.stream().map(entry -> entry.path).toList(),
                "§9 的发货端点是单数 order，且订单列表在复数 orders 下");
        assertEquals(List.of("GET", "POST"), recorded.stream().map(entry -> entry.method).toList());

        String query = query(0);
        assertTrue(query.contains(FILTER_TRIPLE + "[field]=entity_id"), query);
        assertTrue(query.contains(FILTER_TRIPLE + "[value]=7"), query);
        assertTrue(query.contains(FILTER_TRIPLE + "[condition_type]=eq"), "§9 的 condition_type 全集里有 eq");
        assertTrue(query.contains("searchCriteria[pageSize]=1"), "精确取单只要一行: " + query);
    }

    @Test
    void shouldExchangeTheAdminTokenOnceForBothCallsOfTheChain() {
        scripted.add(new Scripted(200, "\"" + ISSUED_TOKEN + "\""));
        scripted.add(new Scripted(200, ORDER_WITH_TWO_LINES));
        scripted.add(new Scripted(200, "{\"entity_id\":9001}"));

        pusher().push(request(true), credentialWithoutPastedToken());

        assertEquals(3, recorded.size(), recorded.toString());
        assertEquals(1, recorded.stream().filter(entry -> "PUT".equals(entry.method)).count(),
                "一次回写只换一次令牌");
        for (Recorded entry : recorded) {
            if ("PUT".equals(entry.method)) {
                continue;
            }
            assertEquals("Bearer " + ISSUED_TOKEN, entry.header("authorization"));
            assertFalse(entry.headers.containsKey("x-api-key"));
        }
        assertFalse(recorded.get(2).body.contains(ADMIN_PASSWORD), "发货体里不该出现口令: " + recorded.get(2).body);
    }

    // ---------- 发货体 ----------

    @Test
    void shouldSendExactlyTheBodyKeysMagentoDocuments() {
        scripted.add(new Scripted(200, ORDER_WITH_TWO_LINES));
        scripted.add(new Scripted(200, "{\"entity_id\":9001,\"status\":\"open\"}"));

        pusher().push(request(true), credential());

        Map<String, Object> body = PlatformJson.object(recorded.get(1).body);
        assertEquals(List.of("items", "notify", "tracks"), new ArrayList<>(body.keySet()),
                "§9 只点名这三个键，多发未核实的键（如 quantity/comment）就是猜: " + body.keySet());

        List<Map<String, Object>> items = PlatformJson.objects(PlatformJson.path(body, "items"));
        assertEquals(2, items.size(), items.toString());
        assertEquals(List.of("order_item_id", "qty"), new ArrayList<>(items.get(0).keySet()));
        assertEquals("71", String.valueOf(items.get(0).get("order_item_id")));
        assertEquals(2, ((Number) items.get(0).get("qty")).intValue());
        // 第二行的数量键名是候选探测的另一种写法（quantity_ordered），解析后同样落到 qty
        assertEquals("72", String.valueOf(items.get(1).get("order_item_id")));
        assertEquals(1, ((Number) items.get(1).get("qty")).intValue());

        List<Map<String, Object>> tracks = PlatformJson.objects(PlatformJson.path(body, "tracks"));
        assertEquals(1, tracks.size());
        assertEquals(List.of("track_number", "title"), new ArrayList<>(tracks.get(0).keySet()),
                "carrier_code 有意不发：ERP 的 carrier 是名称而不是店铺注册的承运商代码");
        assertEquals(TRACKING, tracks.get(0).get("track_number"));
        assertEquals("SF Express", tracks.get(0).get("title"));
        assertEquals(Boolean.TRUE, body.get("notify"), "§9：notify 即 ERP 的 notifyCustomer");
    }

    @Test
    void shouldMirrorNotifyCustomerAndDropAnAbsentCarrier() {
        scripted.add(new Scripted(200, """
                {"items":[{"entity_id":7,"items":[{"item_id":71,"qty":3}]}],"total_count":1}"""));
        scripted.add(new Scripted(200, "{}"));

        pusher().push(new PlatformFulfillmentRequest("7", TRACKING, null, "https://track.example/1", false),
                credential());

        Map<String, Object> body = PlatformJson.object(recorded.get(1).body);
        assertEquals(Boolean.FALSE, body.get("notify"));
        List<Map<String, Object>> items = PlatformJson.objects(PlatformJson.path(body, "items"));
        assertEquals(3, ((Number) items.get(0).get("qty")).intValue(), "qty 的候选键之一是 qty");
        List<Map<String, Object>> tracks = PlatformJson.objects(PlatformJson.path(body, "tracks"));
        assertEquals(List.of("track_number"), new ArrayList<>(tracks.get(0).keySet()),
                "没有承运商名称时不硬造 title");
        assertFalse(body.containsKey("trackingUrl") || body.containsKey("url"),
                "§9 的 tracks 里没有跟踪链接键，不臆造");
    }

    @Test
    void shouldBuildTheResultFromTheShipmentEchoAndOmitAnAbsentTimestamp() {
        scripted.add(new Scripted(200, ORDER_WITH_TWO_LINES));
        scripted.add(new Scripted(200, """
                {"entity_id":9001,"status":"open","created_at":"2026-03-22 08:00:00"}"""));

        PlatformFulfillmentResult result = pusher().push(request(true), credential());

        assertEquals("9001", result.platformFulfillmentId());
        assertEquals("open", result.status());
        assertEquals(2, result.lineItemCount());
        assertEquals(LocalDateTime.of(2026, 3, 22, 8, 0, 0), result.fulfilledAt(),
                "MySQL 裸时间要能落到类型化时间上");

        scripted.add(new Scripted(200, ORDER_WITH_TWO_LINES));
        scripted.add(new Scripted(200, "{}"));
        PlatformFulfillmentResult silent = pusher().push(request(false), credential());
        assertNull(silent.fulfilledAt(), "平台没回时间就是空值而不是编一个");
        assertEquals("7", silent.platformFulfillmentId(), "回显里没有 shipment id 时退回订单号，保证结果可追溯");
    }

    // ---------- 失败口径 ----------

    @Test
    void shouldFailWithoutGoingOnlineWhenTheOrderIsNotShippable() {
        BusinessException noTracking = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest("7", "  ", "SF Express", null, true), credential()));
        assertEquals("MAGENTO_TRACKING_NUMBER_REQUIRED", noTracking.getErrorCode());
        assertTrue(recorded.isEmpty(), "缺运单号就不该出网");

        BusinessException notNumeric = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest("TEE-7", TRACKING, null, null, false),
                        credential()));
        assertEquals("MAGENTO_ORDER_ID_INVALID", notNumeric.getErrorCode());
        BusinessException pathInjection = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest("7/../admin", TRACKING, null, null, false),
                        credential()));
        assertEquals("MAGENTO_ORDER_ID_INVALID", pathInjection.getErrorCode());
        assertTrue(recorded.isEmpty(), "订单号要拼进路径，非法值必须在出网前就拒掉");
    }

    @Test
    void shouldReportAMissingOrderRatherThanShipSomethingElse() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        BusinessException notFound = assertThrows(BusinessException.class, () -> pusher().push(request(true),
                credential()));

        assertEquals("MAGENTO_ORDER_NOT_FOUND", notFound.getErrorCode());
        assertEquals(1, recorded.size(), "找不到订单就不该发 ship 请求");
    }

    @Test
    void shouldRejectAnEchoedOrderThatIsNotTheRequestedOne() {
        scripted.add(new Scripted(200, """
                {"items":[{"entity_id":8,"items":[{"item_id":81,"qty_ordered":1}]}],"total_count":1}"""));

        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(request(true), credential()));

        assertEquals("MAGENTO_ORDER_NOT_FOUND", error.getErrorCode(),
                "平台忽略了 eq 过滤时不能把别人的单发出去");
        assertEquals(1, recorded.size());
    }

    @Test
    void shouldFailWhenNoLineCarriesAResolvableItemId() {
        scripted.add(new Scripted(200, """
                {"items":[{"entity_id":7,"items":[{"sku":"TEE-BLK","qty_ordered":2}]}],"total_count":1}"""));

        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(request(true), credential()));

        assertEquals("MAGENTO_ORDER_ITEMS_UNRESOLVED", error.getErrorCode());
        assertEquals(1, recorded.size(), "解析不出 order_item_id 时宁可不发，空 items 在平台上可能被当成整单发货");
    }

    @Test
    void shouldAcceptTheHashPrefixedFrontEndOrderNumberAndRejectBlanks() {
        assertEquals("7", MagentoFulfillmentPusher.requireOrderId(" #7 "));
        assertEquals("123456", MagentoFulfillmentPusher.requireOrderId("123456"));
        assertThrows(BusinessException.class, () -> MagentoFulfillmentPusher.requireOrderId(null));
        assertThrows(BusinessException.class, () -> MagentoFulfillmentPusher.requireOrderId("000000063x"));
        assertThrows(BusinessException.class, () -> MagentoFulfillmentPusher.requireOrderId("7?x=1"));
    }

    // ---------- 测试装置 ----------

    private MagentoFulfillmentPusher pusher() {
        return new MagentoFulfillmentPusher(api());
    }

    private MagentoRestApi api() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new MagentoRestApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                20);
    }

    private static PlatformFulfillmentRequest request(boolean notifyCustomer) {
        return new PlatformFulfillmentRequest("7", TRACKING, "SF Express", "https://track.example/1",
                notifyCustomer);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                ADMIN_USER, ADMIN_PASSWORD, PASTED_TOKEN, null, null, null, null, null, null);
    }

    private PlatformCredential credentialWithoutPastedToken() {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                ADMIN_USER, ADMIN_PASSWORD, null, null, null, null, null, null, null);
    }

    private String query(int index) {
        return URLDecoder.decode(recorded.get(index).query, StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().toString();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, EMPTY_ORDERS) : scripted.poll();
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
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Scripted(int status, String body) {
    }
}
