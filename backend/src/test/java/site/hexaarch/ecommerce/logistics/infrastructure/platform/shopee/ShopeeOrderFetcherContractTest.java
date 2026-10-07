package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shopee 订单适配器（{@link ShopeeOrderFetcher}）的两步拉取与字段桥接契约测试。
 * <p>
 * 断言的是「怎么把 Shopee 的订单变成导入侧的中立行」：概要来自 {@code get_order_list}、明细来自
 * {@code get_order_detail}（§6 硬规定的两步，收件地址只在第二步出现）、订单数组键按候选探测
 * （{@code order_list} 与 {@code orders} 都要认）、状态桥接到 {@code OrderStatus} 六码、
 * 明细数组原样透传、缺明细时留空而不是崩、单号过滤在取回后生效。<b>响应体字段名未经真实平台验证</b>
 * （无沙箱凭据，归 ISS-065），fixture 只用于验证探测式取值与行键对齐，不能作为平台侧结构的证据。
 * 传输层形状（公共参数进 query、签名、{@code x-env-type}）在 {@link ShopeeOpenApiContractTest} 覆盖，这里不重复。
 *
 * @author kenyon
 */
class ShopeeOrderFetcherContractTest {

    private static final String PARTNER_ID = "pid_1";
    private static final String PARTNER_KEY = "pkey_secret";
    private static final String ACCESS_TOKEN = "atk_live_never_echoed";
    private static final String SHOP_ID = "shop_9";

    private static final String ORDER_SN = "1500001234567890";

    /**
     * 概要页：单号/状态/时间/金额/买家在列表接口就有，收件地址没有（§6：地址只在 detail 出现）
     */
    private static final String LIST_FIXTURE = """
            {"request_id":"l1","error":"","message":"","warning":"","response":{
              "order_list":[{
                "order_sn":"1500001234567890","order_status":"TO_SHIP","create_time":1790929800,
                "currency":"SGD","total_amount":"129.90","payment_method":"SHPEE_PAY","escrow_status":"PAID",
                "buyer_username":"jane"
              }],"more":false}}""";

    /**
     * 明细页：§6 的坑——数组这里叫 {@code orders} 而不是 {@code order_list}，适配器必须两个候选都探；
     * 收件地址/买家邮箱/明细行都在这一步返回
     */
    private static final String DETAIL_FIXTURE = """
            {"request_id":"d1","error":"","message":"","response":{
              "orders":[{
                "order_sn":"1500001234567890",
                "buyer":{"username":"jane","email":"jane@example.com"},
                "shipping":{"name":"Jane Doe","phone":"81234567","country":"SG","state":"Central",
                            "city":"Singapore","address":"1 Marina Boulevard","zip_code":"018989"},
                "item_list":[{"itemid":1,"brand":"Acme","quantity":2,"price":24.5}]
              }]}}""";

    private static final String LIST_EMPTY = """
            {"request_id":"l0","error":"","message":"","response":{"order_list":[],"more":false}}""";
    private static final String DETAIL_EMPTY = """
            {"request_id":"d0","error":"","message":"","response":{"orders":[]}}""";

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
    void shouldMergeSummaryAndDetailIntoNeutralRowKeys() {
        listScripted.add(LIST_FIXTURE);
        detailScripted.add(DETAIL_FIXTURE);

        Map<String, Object> row = fetcher().fetch(orderRequest(null), credential()).get(0);

        assertEquals(ORDER_SN, row.get("orderId"));
        assertEquals("jane", row.get("customerName"), "买家名概要已有，取概要/明细皆可");
        assertEquals("jane@example.com", row.get("email"), "邮箱只在明细里，必须来自 get_order_detail");
        assertEquals("processing", row.get("orderStatus"), "TO_SHIP 是待发货，归 processing");
        assertEquals("2026-03-22 08:30:00", row.get("orderTime"), "create_time 是 epoch 秒要换算成导入时间");
        assertEquals("SGD", row.get("currency"));
        assertEquals("129.90", row.get("totalAmount"));
        assertEquals("SHPEE_PAY", row.get("paymentMethod"));
        assertEquals("PAID", row.get("paymentStatus"));
        assertEquals("Jane Doe", row.get("recipientName"), "收件人来自明细的 shipping");
        assertEquals("81234567", row.get("phoneNumber"));
        assertEquals("SG", row.get("country"));
        assertEquals("Central", row.get("province"));
        assertEquals("Singapore", row.get("city"));
        assertEquals("1 Marina Boulevard", row.get("addressLine1"));
        assertEquals("018989", row.get("zipCode"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        listScripted.add(LIST_FIXTURE);
        detailScripted.add(DETAIL_FIXTURE);

        Map<String, Object> row = fetcher().fetch(orderRequest(null), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("shipping"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("item_list"));
    }

    @Test
    void shouldReadOrdersWhenTheListArrayIsKeyedAsOrdersInsteadOfOrderList() {
        // §6 未核实数组键名：order_list / orders 都要能探到
        listScripted.add("""
                {"error":"","response":{"orders":[{"order_sn":"SN-alt","order_status":"UNPAID"}],"more":false}}""");
        detailScripted.add(DETAIL_EMPTY);

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null), credential());

        assertEquals(1, rows.size(), "列表数组叫 orders 也要认出来");
        assertEquals("SN-alt", rows.get(0).get("orderId"));
        assertEquals("pending", rows.get(0).get("orderStatus"));
    }

    @Test
    void shouldPassItemArrayThroughWithoutRewritingPlatformKeys() {
        listScripted.add(LIST_FIXTURE);
        detailScripted.add(DETAIL_FIXTURE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) fetcher().fetch(orderRequest(null), credential()).get(0).get("lineItems");

        assertEquals(1, items.size());
        assertEquals("Acme", items.get(0).get("brand"), "明细数组元素结构未被 §6 核实，原样透传以便实测后一次性对齐");
        assertEquals(2, items.get(0).get("quantity"));
    }

    @Test
    void shouldLeaveAddressBlankWhenDetailDoesNotReturnTheOrderInsteadOfFailing() {
        listScripted.add(LIST_FIXTURE);
        detailScripted.add(DETAIL_EMPTY);

        Map<String, Object> row = fetcher().fetch(orderRequest(null), credential()).get(0);

        assertEquals(ORDER_SN, row.get("orderId"), "概要能出行，明细缺失只让地址类字段留空");
        assertEquals("jane", row.get("recipientName"), "无 shipping 时收件人退到买家名");
        assertNull(row.get("addressLine1"), "地址在概要里拿不到，留空由导入侧必填校验去报这一行");
        assertNull(row.get("zipCode"));
        assertEquals(List.of(), row.get("lineItems"), "缺明细要出行但不抛异常");
    }

    @Test
    void shouldFollowCursorIntoSecondPageAndDedupOrderIds() {
        listScripted.add("""
                {"error":"","response":{"order_list":[{"order_sn":"SN1","order_status":"TO_SHIP"}],
                 "more":true,"next_cursor":"C2"}}""");
        listScripted.add("""
                {"error":"","response":{"order_list":[{"order_sn":"SN2","order_status":"COMPLETED"}],
                 "more":false}}""");
        detailScripted.add(DETAIL_EMPTY);
        detailScripted.add(DETAIL_EMPTY);

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null), credential());

        List<Recorded> lists = listCalls();
        assertEquals(2, lists.size(), "more=true 且 next_cursor 推进时跟游标再翻一页");
        assertEquals("C2", PlatformJson.object(lists.get(1).body).get("cursor"), "下一页游标由上一页响应链过来");
        assertEquals(List.of("SN1", "SN2"), rows.stream().map(row -> row.get("orderId")).toList());
    }

    @Test
    void shouldStopPagingWhenTheCursorDoesNotAdvance() {
        for (int i = 0; i < 10; i++) {
            listScripted.add("""
                    {"error":"","response":{"order_list":[{"order_sn":"SN1"}],"more":true,"next_cursor":"SAME"}}""");
            detailScripted.add(DETAIL_EMPTY);
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(orderRequest(1000), credential());

        assertEquals(2, listCalls().size(), "游标不再推进就收手，不能空转到页数上限 20");
        assertEquals(1, rows.size(), "重复页里的同一单号不能重复出行");
    }

    @Test
    void shouldFilterByOrderNumberAfterFetchingBecauseTheListEndpointIsWindowed() {
        listScripted.add("""
                {"error":"","response":{"order_list":[
                  {"order_sn":"SN1","order_status":"TO_SHIP"},
                  {"order_sn":"SN2","order_status":"COMPLETED"}],"more":false}}""");
        detailScripted.add(DETAIL_EMPTY);

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest("SN2"), credential());

        assertEquals(1, rows.size());
        assertEquals("SN2", rows.get(0).get("orderId"));
    }

    @Test
    void shouldFailWithPlatformMessageWhenEnvelopeErrorIsNonEmpty() {
        listScripted.add("""
                {"request_id":"r","error":"error_auth_failure","message":"invalid access token"}""");

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(null), credential()));

        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertTrue(error.getMessage().contains("error_auth_failure"), error.getMessage());
        assertTrue(error.getMessage().contains("invalid access token"), error.getMessage());
        assertFalse(error.getMessage().contains(ACCESS_TOKEN), "异常消息不得回显令牌: " + error.getMessage());
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(null)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldBridgeStatusTableWithoutInventingUnseenValues() {
        assertEquals("pending", ShopeeOrderFetcher.bridgeStatus("UNPAID"));
        assertEquals("processing", ShopeeOrderFetcher.bridgeStatus("TO_CONFIRM"));
        assertEquals("processing", ShopeeOrderFetcher.bridgeStatus("TO_SHIP"));
        assertEquals("shipped", ShopeeOrderFetcher.bridgeStatus("TO_ARRIVE"));
        assertEquals("completed", ShopeeOrderFetcher.bridgeStatus("COMPLETED"));
        assertEquals("cancelled", ShopeeOrderFetcher.bridgeStatus("CANCELLED"));
        assertEquals("cancelled", ShopeeOrderFetcher.bridgeStatus("IN_CANCEL"));
        assertEquals("SomethingNew", ShopeeOrderFetcher.bridgeStatus("SomethingNew"), "认不出要原样透出而不是硬塞");
        assertNull(ShopeeOrderFetcher.bridgeStatus("  "));
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

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.SHOPEE, "tenant-1", "SHOPEE", storeUrl,
                PARTNER_ID, PARTNER_KEY, ACCESS_TOKEN, null, null, "rt_test", null, SHOP_ID, null);
    }

    private static PlatformOrderFetchRequest orderRequest(String orderNumber) {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPEE", null, null, null,
                orderNumber, null, null, 50, 1);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPEE", null, null, null,
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"),
                limit, 1);
    }

    private List<Recorded> listCalls() {
        return recorded.stream().filter(r -> r.path.endsWith("get_order_list")).toList();
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Recorded entry = new Recorded();
        entry.path = path;
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recorded.add(entry);

        boolean isList = path.endsWith("get_order_list");
        Deque<String> queue = isList ? listScripted : detailScripted;
        String body = queue.isEmpty() ? (isList ? LIST_EMPTY : DETAIL_EMPTY) : queue.poll();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class Recorded {
        private String path;
        private String body;
    }
}
