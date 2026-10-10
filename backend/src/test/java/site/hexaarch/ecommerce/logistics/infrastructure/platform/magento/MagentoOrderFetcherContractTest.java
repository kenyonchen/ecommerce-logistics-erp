package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * Magento 订单拉取的契约测试：{@code searchCriteria} 的形状与 {@code total_count} 驱动的翻页。
 * <p>
 * 断言的都是 §9（docs/spec/platform-api-reference.md）写死的规则，按「最容易静默出错」排序：
 * <ol>
 *   <li>{@code field}/{@code value}/{@code condition_type} <b>三者各出现一次</b>——少任一个平台会静默丢掉
 *       整个条件（不报错、返回全量或空），所以这里按字面计数而不是「有就行」；</li>
 *   <li>上下界必须在<b>两个</b> {@code filter_groups} 里（§9：组间 AND、组内 OR），写进同一组增量窗口就废了；</li>
 *   <li>翻页靠 {@code currentPage}（从 1 起）并由 {@code total_count} 判停，平台不按 {@code currentPage}
 *       前进时要有兜底而不是在页数上限上空转；</li>
 *   <li>列表里的 {@code status} 是<b>标签字符串</b>，必须桥到 ERP 的六个码。</li>
 * </ol>
 * 行键只允许 {@code OrderImportTargetField} 认得的别名，否则导入映射阶段会直接丢弃。
 * <p>
 * 注意 fixture 里的订单对象键名（{@code entity_id}/{@code grand_total}/{@code shipping_address} 等）
 * <b>不是</b>平台侧结构的证据：§9 核实的是查询语法与信封，字段名待实测校正（ISS-065），
 * 这些用例验证的是「候选键探测 + 桥接」这套映射逻辑本身。
 *
 * @author kenyon
 */
class MagentoOrderFetcherContractTest {

    private static final String PASTED_TOKEN = "pasted-integration-token-never-echoed";
    private static final String GROUP_PREFIX = "searchCriteria[filter_groups]";
    private static final DateTimeFormatter QUERY_VALUE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String EMPTY_ORDERS = "{\"items\":[],\"total_count\":0}";

    /**
     * 一条尽量贴近 Magento 口径的订单：金额是 JSON 数字（不是字符串）、{@code street} 是数组、
     * 时间是 MySQL 的裸 {@code yyyy-MM-dd HH:mm:ss}
     */
    private static final String RICH_ORDER_FIXTURE = """
            {"items":[{
              "entity_id":101,
              "increment_id":"000000101",
              "state":"processing",
              "status":"Processing",
              "created_at":"2026-01-25 08:15:30",
              "updated_at":"2026-01-25 09:00:00",
              "customer_email":"buyer@example.com",
              "grand_total":123.45,
              "order_currency_code":"USD",
              "shipping_address":{
                "firstname":"Ada","lastname":"Lovelace",
                "street":["1 Bldg","Apt 3"],
                "city":"London","region":"Middx","postcode":"NW1",
                "telephone":"+44 20 7946 0958","country_id":"GB"},
              "billing_address":{"firstname":"Ada","lastname":"Lovelace","country_id":"GB"},
              "payment":{"method":"checkmo","status":"paid"},
              "items":[{"item_id":501,"sku":"TEE-BLK","name":"Tee","qty_ordered":2,"price":9.99}]
            }],"search_criteria":[],"total_count":1}""";

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

    // ---------- searchCriteria 形状 ----------

    @Test
    void shouldSendTheTimeFilterWithFieldValueAndConditionTypeAppearingOnceEach() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        fetcher().fetch(orderRequest(from, null, 10), credential());

        String query = query(0);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][field]=updated_at"), query);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][value]=" + QUERY_VALUE.format(from)), query);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][condition_type]=from"), query);
        // §9：三个键各出现一次；重复出现会被平台当成多个条件
        assertEquals(1, count(query, "[field]"), query);
        assertEquals(1, count(query, "[value]"), query);
        assertEquals(1, count(query, "[condition_type]"), query);
    }

    @Test
    void shouldSplitBothBoundsIntoSeparateFilterGroupsBecauseGroupsAreAnd() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 3, 12, 0);
        fetcher().fetch(orderRequest(from, to, 10), credential());

        String query = query(0);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][condition_type]=from"), query);
        assertTrue(query.contains(GROUP_PREFIX + "[1][filters][0][condition_type]=to"),
                "§9：组内是 OR，两个界挤在同一组就从区间变成并集: " + query);
        assertTrue(query.contains(GROUP_PREFIX + "[1][filters][0][value]=" + QUERY_VALUE.format(to)), query);
        assertEquals(2, count(query, "[field]"), query);
        assertEquals(2, count(query, "[condition_type]"), query);
    }

    @Test
    void shouldFilterOnCreatedAtWhenOnlyTheUpperBoundIsGiven() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        fetcher().fetch(orderRequest(null, LocalDateTime.of(2026, 10, 3, 12, 0), 10), credential());

        String query = query(0);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][field]=created_at"), query);
        assertTrue(query.contains(GROUP_PREFIX + "[0][filters][0][condition_type]=to"), query);
        assertEquals(1, count(query, "[field]"), query);
    }

    @Test
    void shouldSendNoTimeFilterAtAllWhenTheWindowIsEmpty() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        fetcher().fetch(orderRequest(null, null, 10), credential());

        String query = query(0);
        assertEquals("GET", recorded.get(0).method, "订单拉取是只读的");
        assertEquals("/rest/default/V1/orders", recorded.get(0).path);
        assertEquals("", recorded.get(0).body, "只读调用不带请求体");
        assertFalse(query.contains("filter_groups"), "§9 不要求时间界必填，所以不编回溯窗口: " + query);
        assertTrue(query.contains("searchCriteria[pageSize]=10"), query);
        assertTrue(query.contains("searchCriteria[currentPage]=1"), "§9：currentPage 从 1 起");
    }

    @Test
    void shouldStartPagingFromTheRequestedPage() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "MAGENTO", null, null, null,
                null, null, null, 10, 3), credential());

        assertTrue(query(0).contains("searchCriteria[currentPage]=3"), query(0));
    }

    // ---------- 翻页 ----------

    @Test
    void shouldKeepPagingWhileTotalCountPromisesMoreRows() {
        scripted.add(new Scripted(200, orders(2, 4, 101, 102)));
        scripted.add(new Scripted(200, orders(2, 4, 103, 104)));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(4, rows.size(), rows.toString());
        assertEquals(2, recorded.size(), recorded.toString());
        assertTrue(query(1).contains("searchCriteria[currentPage]=2"), query(1));
    }

    @Test
    void shouldStopAsSoonAsTotalCountIsConsumed() {
        scripted.add(new Scripted(200, orders(2, 2, 101, 102)));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(2, rows.size());
        assertEquals(1, recorded.size(), "已消费数到达 total_count 就不该再翻一页");
    }

    @Test
    void shouldStopWhenThePlatformIgnoresCurrentPageAndRepeatsTheSameRows() {
        scripted.add(new Scripted(200, orders(2, 100, 101, 102)));
        scripted.add(new Scripted(200, orders(2, 100, 101, 102)));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(2, rows.size(), "跨页重复的单不能重复出行");
        assertEquals(2, recorded.size(), "整页都是见过的行即判定平台没前进，收手而不是空转到页数上限");
    }

    @Test
    void shouldHonourThePageCeilingEvenWhenTotalCountPromisesMore() {
        scripted.add(new Scripted(200, orders(2, 100, 101, 102)));
        scripted.add(new Scripted(200, orders(2, 100, 103, 104)));

        List<Map<String, Object>> rows = new MagentoOrderFetcher(api(1)).fetch(orderRequest(null, null, 10),
                credential());

        assertEquals(2, rows.size());
        assertEquals(1, recorded.size(), "platform.magento.cursor.max-pages 是失控保险丝");
    }

    @Test
    void shouldStopAtTheRequestedTotalLimitEvenWhenThePlatformReturnsMore() {
        scripted.add(new Scripted(200, orders(4, 100, 101, 102, 103, 104)));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 3), credential());

        assertEquals(3, rows.size(), rows.toString());
        assertEquals(1, recorded.size(), "行数到达 limit 就不该再翻下一页");
    }

    // ---------- 行映射与桥接 ----------

    @Test
    void shouldMapTheOrderObjectOntoNeutralImportKeys() {
        scripted.add(new Scripted(200, RICH_ORDER_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(null, null, 10), credential()).get(0);

        // §9 的发货端点要把数字主键拼进 /V1/order/<orderId>/ship，所以行里的订单号取 entity_id 而不是 increment_id
        assertEquals("101", row.get("orderId"));
        assertFalse("000000101".equals(row.get("orderId")), "取前台单号会让回写全部打不通");
        assertEquals("Ada Lovelace", row.get("customerName"));
        assertEquals("buyer@example.com", row.get("email"));
        assertEquals("processing", row.get("orderStatus"));
        assertEquals("2026-01-25 08:15:30", row.get("orderTime"), "MySQL 裸时间要能落到导入侧格式");
        assertEquals("USD", row.get("currency"));
        assertEquals("123.45", row.get("totalAmount"), "金额不能被截成 123");
        assertEquals("checkmo", row.get("paymentMethod"));
        assertEquals("paid", row.get("paymentStatus"));
        assertEquals("Ada Lovelace", row.get("recipientName"));
        assertEquals("+44 20 7946 0958", row.get("phoneNumber"));
        assertEquals("GB", row.get("country"));
        assertEquals("Middx", row.get("province"));
        assertEquals("London", row.get("city"));
        assertEquals("1 Bldg, Apt 3", row.get("addressLine1"), "street 是数组，不能 toString 成 [1 Bldg, Apt 3]");
        assertEquals("NW1", row.get("zipCode"));
        assertEquals(1, ((List<?>) row.get("lineItems")).size());

        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("entity_id"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("increment_id"));
        assertFalse(row.containsKey("shipping_address"));
        for (Recorded request : recorded) {
            assertEquals("Bearer " + PASTED_TOKEN, request.header("authorization"), "§9 只认 Bearer");
            assertFalse(request.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头");
            assertFalse(request.headers.containsKey("client-id"), "Client-Id 是 Ozon 的头");
        }
    }

    @Test
    void shouldFallBackToTheBillingAddressWhenNoShippingAddressIsReturned() {
        scripted.add(new Scripted(200, """
                {"items":[{"entity_id":102,"status":"New","created_at":"2026-03-22 10:00:00",
                  "billing_address":{"firstname":"Grace","lastname":"Hopper","street":"1 Navy Rd",
                    "city":"Arlington","region":"VA","postcode":"22201","telephone":"703-555-0100",
                    "country_id":"US"},
                  "extension_attributes":{"payment":{"method":"purchaseorder","status":"processing"}}
                }],"total_count":1}"""));

        Map<String, Object> row = fetcher().fetch(orderRequest(null, null, 10), credential()).get(0);

        assertEquals("Grace Hopper", row.get("recipientName"));
        assertEquals("1 Navy Rd", row.get("addressLine1"), "street 是标量时原样用");
        assertEquals("US", row.get("country"));
        assertEquals("purchaseorder", row.get("paymentMethod"), "payment 挂在 extension_attributes 下也要探到");
        assertEquals("pending", row.get("orderStatus"), "§9：new 是标签文案，要桥成 ERP 的 pending");
    }

    @Test
    void shouldLeaveUnresolvedFieldsBlankInsteadOfInventingValues() {
        scripted.add(new Scripted(200, "{\"items\":[{\"entity_id\":103}],\"total_count\":1}"));

        Map<String, Object> row = fetcher().fetch(orderRequest(null, null, 10), credential()).get(0);

        assertNull(row.get("customerName"));
        assertNull(row.get("orderStatus"));
        assertNull(row.get("totalAmount"));
        assertNull(row.get("orderTime"));
        assertTrue(((List<?>) row.get("lineItems")).isEmpty());
    }

    @Test
    void shouldFilterByOrderNumberClientSideBecauseTheListEndpointHasNoSuchParameter() {
        scripted.add(new Scripted(200, orders(2, 2, 101, 102)));

        List<Map<String, Object>> rows = fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "MAGENTO",
                null, null, null, "102", null, null, 10, 1), credential());

        assertEquals(1, rows.size(), rows.toString());
        assertEquals("102", rows.get(0).get("orderId"));
        assertFalse(query(0).contains("[value]=102"), "§9 的列表端点没有按订单号过滤的参数，只能取回后筛");
    }

    @Test
    void shouldBridgeMagentoStatesAndPassUnknownLabelsThrough() {
        assertEquals("cancelled", MagentoOrderFetcher.bridgeStatus("canceled"));
        assertEquals("cancelled", MagentoOrderFetcher.bridgeStatus("Cancelled"));
        assertEquals("completed", MagentoOrderFetcher.bridgeStatus("complete"));
        assertEquals("completed", MagentoOrderFetcher.bridgeStatus("Closed"));
        assertEquals("processing", MagentoOrderFetcher.bridgeStatus("Processing"));
        assertEquals("shipped", MagentoOrderFetcher.bridgeStatus("Shipped"));
        assertEquals("pending", MagentoOrderFetcher.bridgeStatus("Pending Payment"));
        assertEquals("pending", MagentoOrderFetcher.bridgeStatus("payment_review"));
        assertEquals("pending", MagentoOrderFetcher.bridgeStatus("holded"));
        assertEquals("pending", MagentoOrderFetcher.bridgeStatus("New"));
        // 自定义文案「Pending Shipment」按在途归类，不能被 pend 抢先判成待处理
        assertEquals("shipped", MagentoOrderFetcher.bridgeStatus("Pending Shipment"));
        assertEquals("fraud_flagged", MagentoOrderFetcher.bridgeStatus("fraud_flagged"),
                "认不出的文案原样透出，让运营看得见平台侧出现了新状态");
        assertNull(MagentoOrderFetcher.bridgeStatus("  "));
    }

    @Test
    void shouldRequireCredentialsAndRefuseTheCredentiallessOverload() {
        BusinessException singleArg = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(null, null, 10)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", singleArg.getErrorCode(),
                "Magento 的令牌与 base URL 只能来自集成凭据，单参重载必须拒掉而不是凭空构造");
        assertFalse(singleArg.getMessage().contains("never-echoed"), "错误消息不得回显凭据值: " + singleArg.getMessage());
        assertEquals(PlatformAuthType.MAGENTO.name(), fetcher().type());
        assertTrue(recorded.isEmpty(), "还没解析出凭据就不该出网");
    }

    // ---------- 测试装置 ----------

    private MagentoOrderFetcher fetcher() {
        return new MagentoOrderFetcher(api(20));
    }

    private MagentoRestApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new MagentoRestApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                "erp_sync", "admin-pass-never-echoed", PASTED_TOKEN, null, null, null, null, null, null);
    }

    private static PlatformOrderFetchRequest orderRequest(LocalDateTime updatedFrom, LocalDateTime updatedTo,
                                                          int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "MAGENTO", null, null, null, null,
                updatedFrom, updatedTo, limit, 1);
    }

    /**
     * §9 的信封：{@code items[]} + {@code total_count}
     */
    private static String orders(int itemCount, int totalCount, int... entityIds) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < itemCount; index++) {
            if (index > 0) {
                items.append(',');
            }
            items.append("{\"entity_id\":").append(entityIds[index]).append(",\"status\":\"processing\"}");
        }
        return "{\"items\":[" + items + "],\"search_criteria\":[],\"total_count\":" + totalCount + "}";
    }

    private String query(int index) {
        return URLDecoder.decode(recorded.get(index).query, StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
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
