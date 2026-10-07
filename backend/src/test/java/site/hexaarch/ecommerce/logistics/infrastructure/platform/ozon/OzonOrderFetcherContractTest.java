package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ozon 订单适配器的请求形状、逐单回取与字段桥接契约测试。
 * <p>
 * 断言依据全部来自 §8（docs/spec/platform-api-reference.md）：
 * {@code POST /v3/posting/fbs/list} 的 {@code {dir, sort, filter:{since, until, limit, offset, status?}, with}} 体、
 * {@code filter.limit} 上限 1000、<b>所有时间必须带时区偏移</b>（裸 {@code LocalDateTime} 会被判 {@code bad time}，
 * 所以这里用「必须匹配带偏移的时间戳」而不是「包含 T」来断言）、
 * <b>列表不给明细故每一单都要再打一次 {@code /v2/posting/fbs/get}</b>，以及 FBO 端点一律不发。
 * <p>
 * posting 对象的字段名未经真实平台验证（§8 只核实了端点与信封；无沙箱凭据，归 ISS-065），
 * 因此这里的 fixture 只用于验证候选键探测、状态桥接与中立行键对齐，不能当作平台侧结构的证据。
 *
 * @author kenyon
 */
class OzonOrderFetcherContractTest {

    private static final String CLIENT_ID = "215000371";
    private static final String API_KEY = "secret-api-key-never-echoed";
    private static final String POSTING_1 = "0998957729-0001";
    private static final String POSTING_2 = "0998957729-0002";

    /**
     * §8 的坑：时间戳必须带偏移。这里要求以 {@code +HH:MM}/{@code -HH:MM}（或 {@code Z}）结尾，
     * 任何裸 {@code LocalDateTime} 形态都过不了这条断言。
     */
    private static final Pattern OFFSET_TIMESTAMP =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?([+-]\\d{2}:\\d{2}|Z)$");

    private static final String LIST_FIXTURE = """
            {"result":{"posts":[
              {"posting_number":"0998957729-0001","status":"awaiting_packaging","created_at":"2026-01-25T08:30:00+03:00"},
              {"posting_number":"0998957729-0002","status":"delivering"}
            ],"has_more":false},"has_more":false}""";

    private static final String LIST_NO_MORE_FIXTURE = """
            {"result":{"posts":[],"has_more":false},"has_more":false}""";

    /**
     * 明细响应：§8 说 {@code /v2/posting/fbs/get} 返回单个 posting；这里按 {@code {result:{…}}} 信封给，
     * {@code pricing} 用 JSON 字符串形态（适配器两种形态都探）
     */
    private static final String DETAIL_1_FIXTURE = """
            {"result":{
              "posting_number":"0998957729-0001",
              "status":"awaiting_packaging",
              "created_at":"2026-01-25T08:30:00+03:00",
              "in_process_at":"2026-01-25T09:00:00+03:00",
              "shipment_date":"2026-03-22T10:00:00+03:00",
              "customer":{"name":"Ivan Petrov","email":"ivan@example.ru"},
              "address":{"name":"Ivan Petrov","phone":"+79005553322","country":"RU",
                         "region":"Moscow Oblast","city":"Moscow","address":"Lenina 1","zip_code":"101000"},
              "financial_data":{"products":[
                {"product_id":4900,"name":"Classic Tee","quantity":2,
                 "payer_total_amount":"150.00","currency_code":"RUB"}]},
              "pricing":"{\\"total\\":{\\"amount\\":\\"150.00\\",\\"currency_code\\":\\"RUB\\"}}"
            }}""";

    private static final String DETAIL_2_FIXTURE = """
            {"result":{"posting_number":"0998957729-0002","status":"delivering",
                       "created_at":"2026-01-25T11:15:00+03:00"}}""";

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

    // ---------- 列表请求形状 ----------

    @Test
    void shouldSendVerifiedListBodyAndOffsetBoundedWindow() {
        scripted.add(new Scripted(200, LIST_NO_MORE_FIXTURE));

        fetcher().fetch(new PlatformOrderFetchRequest("tenant-1", "OZON", null, null, null,
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"),
                20, 1), credential());

        Recorded list = recorded.get(0);
        assertEquals("POST", list.method);
        assertEquals(OzonSellerApi.ORDERS_LIST_PATH, list.path);
        assertEquals(CLIENT_ID, list.header(OzonSellerApi.CLIENT_ID_HEADER));
        assertEquals(API_KEY, list.header(OzonSellerApi.API_KEY_HEADER));
        assertFalse(list.headers.containsKey("authorization"), "§8：Ozon 没有 Bearer/OAuth");
        assertFalse(list.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头");

        Map<String, Object> body = PlatformJson.object(list.body);
        assertEquals("asc", body.get("dir"));
        assertEquals("created_at", body.get("sort"));
        assertTrue(body.containsKey("with"), "§8 的体里有 with 这个键");
        Map<String, Object> filter = PlatformJson.nested(body, "filter");
        // ZoneOffset.UTC 按 RFC3339 写成 Z，它同样是 §8 要求的「带时区偏移」形态
        assertEquals("2026-01-25T00:00:00Z", filter.get("since"));
        assertEquals("2026-03-22T00:00:00Z", filter.get("until"));
        assertEquals(20, filter.get("limit"));
        assertEquals(0, filter.get("offset"));
        assertFalse(filter.containsKey("status"), "§8 的 status 是可选键，同步不按状态收窄，不该发");
    }

    @Test
    void shouldAlwaysWriteEveryOutboundTimestampWithATimezoneOffset() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));

        fetcher().fetch(orderRequest(null, null, 10), credential());

        List<String> offenders = new ArrayList<>();
        for (Recorded request : recorded) {
            collectDateTimeLikeValues(PlatformJson.object(request.body), "", offenders);
        }
        assertTrue(offenders.isEmpty(), "§8 的坑：任何不带偏移的 *At 值都会让平台报 bad time -> " + offenders);
        // 没有显式时间界时也要给一个有界窗口，且窗口两端都带偏移
        Map<String, Object> filter = PlatformJson.nested(PlatformJson.object(recorded.get(0).body), "filter");
        LocalDateTime since = PlatformJson.toUtcLocalDateTime(String.valueOf(filter.get("since")));
        LocalDateTime until = PlatformJson.toUtcLocalDateTime(String.valueOf(filter.get("until")));
        assertTrue(until.isAfter(since) && Duration.between(since, until).toDays() == 30,
                "缺省回溯窗口应是 30 天，实测 " + since + " ~ " + until);
    }

    @Test
    void shouldClampFilterLimitToOneThousand() {
        scripted.add(new Scripted(200, LIST_NO_MORE_FIXTURE));

        fetcher().fetch(orderRequest(null, null, 5000), credential());

        Map<String, Object> filter = PlatformJson.nested(PlatformJson.object(recorded.get(0).body), "filter");
        assertEquals(1000, filter.get("limit"), "§8：filter.limit 上限 1000，超出的必须压回来");
    }

    // ---------- 逐单回取 ----------

    @Test
    void shouldReFetchDetailOncePerPostingBecauseTheListCarriesNoDetail() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(3, recorded.size(), "§8：列表不返回明细，2 单就是 1 次列表 + 2 次明细");
        assertEquals(OzonSellerApi.ORDERS_LIST_PATH, recorded.get(0).path);
        for (int i = 1; i < recorded.size(); i++) {
            assertEquals(OzonSellerApi.ORDER_DETAIL_PATH, recorded.get(i).path);
            Map<String, Object> body = PlatformJson.object(recorded.get(i).body);
            assertEquals(List.of("posting_number"), List.copyOf(body.keySet()),
                    "§8 的明细体是 {posting_number, language?}，language 未核实取值故不发");
        }
        assertEquals(POSTING_1, PlatformJson.object(recorded.get(1).body).get("posting_number"));
        assertEquals(POSTING_2, PlatformJson.object(recorded.get(2).body).get("posting_number"));
        assertEquals(2, rows.size());
        assertEquals(POSTING_1, rows.get(0).get("orderId"));
        assertEquals(POSTING_2, rows.get(1).get("orderId"));
        for (Recorded request : recorded) {
            assertFalse(request.path.contains("fbo"), "§8：FBO(/v3/posting/fbo/list) 明确不在本轮范围内");
        }
    }

    @Test
    void shouldCapTotalRowsAtRequestedLimitAndNotFanOutBeyondIt() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 1), credential());

        assertEquals(1, rows.size(), "request.limit() 是总行数上限");
        assertEquals(2, recorded.size(), "明细回取要受同一个上限约束：1 次列表 + 1 次明细，不能把整页都回取一遍");
    }

    @Test
    void shouldAdvanceOffsetOnContinuationPageAndStopWhenHasMoreIsFalse() {
        scripted.add(new Scripted(200, LIST_FIXTURE.replace(
                "],\"has_more\":false},\"has_more\":false}", "],\"has_more\":true},\"has_more\":true}")));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));
        scripted.add(new Scripted(200, LIST_NO_MORE_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(4, recorded.size());
        assertEquals(OzonSellerApi.ORDERS_LIST_PATH, recorded.get(3).path);
        Map<String, Object> secondFilter =
                PlatformJson.nested(PlatformJson.object(recorded.get(3).body), "filter");
        assertEquals(10, secondFilter.get("offset"), "第二页的 offset 要跟着单页条数推进");
        assertEquals(10, secondFilter.get("limit"));
        assertEquals(2, rows.size(), "第二页没有单号就结束，行数是第一页的 2 条");
    }

    @Test
    void shouldFetchASinglePostingDirectlyWhenOrderNumberIsGiven() {
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(
                new PlatformOrderFetchRequest("tenant-1", "OZON", null, null, null, "  " + POSTING_1 + " ",
                        null, null, 10, 1), credential());

        assertEquals(1, recorded.size(), "§8 的 /v2/posting/fbs/get 就是按单号精确取单，不必先拉列表");
        assertEquals(OzonSellerApi.ORDER_DETAIL_PATH, recorded.get(0).path);
        assertEquals(POSTING_1, PlatformJson.object(recorded.get(0).body).get("posting_number"));
        assertEquals(1, rows.size());
        assertEquals(POSTING_1, rows.get(0).get("orderId"));
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutLeakingTheApiKey() {
        scripted.add(new Scripted(401, "{\"code\":0,\"message\":\"api key is invalid\"}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(null, null, 5), credential()));

        assertEquals(1, recorded.size(), "§8：401 是凭据问题，重试没有意义");
        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains(API_KEY), "异常消息不得回显 Api-Key: " + error.getMessage());
    }

    @Test
    void shouldBackOffAndRetryOnTooManyRequests() {
        scripted.add(new Scripted(429, "{\"code\":0,\"message\":\"too many requests\"}"));
        scripted.add(new Scripted(200, LIST_NO_MORE_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 5), credential());

        assertEquals(2, recorded.size(), "429 属于暂时性失败，应按 §2.3 的退避重试");
        assertTrue(rows.isEmpty());
    }

    // ---------- 字段桥接 ----------

    @Test
    void shouldBridgePostingFieldsIntoNeutralRowKeys() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(null, null, 10), credential()).get(0);

        assertEquals(POSTING_1, row.get("orderId"));
        assertEquals("Ivan Petrov", row.get("customerName"));
        assertEquals("ivan@example.ru", row.get("email"));
        assertEquals("processing", row.get("orderStatus"));
        // +03:00 的时刻要归一到 UTC 墙上时间，导入侧只认 yyyy-MM-dd HH:mm:ss
        assertEquals("2026-01-25 05:30:00", row.get("orderTime"));
        assertEquals("RUB", row.get("currency"));
        assertEquals("150.00", row.get("totalAmount"));
        assertNull(row.get("paymentMethod"), "posting 的支付字段名未经核实，探不到就留空而不是断言");
        assertEquals("Ivan Petrov", row.get("recipientName"));
        assertEquals("+79005553322", row.get("phoneNumber"));
        assertEquals("RU", row.get("country"));
        assertEquals("Moscow Oblast", row.get("province"));
        assertEquals("Moscow", row.get("city"));
        assertEquals("Lenina 1", row.get("addressLine1"));
        assertEquals("101000", row.get("zipCode"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("lineItems");
        assertEquals(1, items.size());
        assertEquals("Classic Tee", items.get(0).get("name"), "明细结构未核实，原样透传平台键");
        assertEquals(2, items.get(0).get("quantity"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));

        Map<String, Object> row = fetcher().fetch(orderRequest(null, null, 10), credential()).get(0);

        assertTrue(row.keySet().containsAll(List.of("orderId", "customerName", "email", "orderStatus", "orderTime",
                "currency", "totalAmount", "paymentMethod", "paymentStatus", "recipientName", "phoneNumber",
                "country", "province", "city", "addressLine1", "zipCode", "lineItems")), row.keySet().toString());
        for (String key : row.keySet()) {
            assertTrue(OrderImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("financial_data"), "不得把平台原始键直接抛给导入侧");
        assertFalse(row.containsKey("address"));
    }

    @Test
    void shouldKeepTheRowFromTheListSummaryWhenDetailIsMissing() {
        scripted.add(new Scripted(200, LIST_FIXTURE.replace(
                "\"posting_number\":\"" + POSTING_2 + "\",\"status\":\"delivering\"",
                "\"posting_number\":\"" + POSTING_2 + "\",\"status\":\"cancelled\"")));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, "{}"));

        List<Map<String, Object>> rows = fetcher().fetch(orderRequest(null, null, 10), credential());

        assertEquals(2, rows.size(), "明细接口空响应不能丢整行，导入侧还要靠这一行报错");
        assertEquals("cancelled", rows.get(1).get("orderStatus"), "明细没给就退回列表条目的状态");
        assertEquals(POSTING_2, rows.get(1).get("orderId"));
        assertNull(rows.get(1).get("addressLine1"), "列表条目没有地址，地址键留空");
        assertEquals(List.of(), rows.get(1).get("lineItems"));
    }

    @Test
    void shouldBridgeStatusExactlyPerTheAgreedTable() {
        assertEquals("processing", OzonOrderFetcher.bridgeStatus("awaiting_packaging"));
        assertEquals("processing", OzonOrderFetcher.bridgeStatus("awaiting_deliver"));
        assertEquals("shipped", OzonOrderFetcher.bridgeStatus("delivering"));
        assertEquals("shipped", OzonOrderFetcher.bridgeStatus("driver_pickup"));
        assertEquals("completed", OzonOrderFetcher.bridgeStatus("delivered"));
        assertEquals("cancelled", OzonOrderFetcher.bridgeStatus("cancelled"));
        assertEquals("processing", OzonOrderFetcher.bridgeStatus("Awaiting_Packaging"), "大小写与分隔符要归一");
        assertEquals("awaiting_registration", OzonOrderFetcher.bridgeStatus("awaiting_registration"),
                "表外的状态原样透出，不要硬塞成 pending 掩盖平台新增的取值");
        assertNull(OzonOrderFetcher.bridgeStatus("  "));
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(orderRequest(null, null, 5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 连通性测试 ----------

    @Test
    void shouldProbeWithCheapestAuthenticatedReadAndHideBothCredentials() {
        scripted.add(new Scripted(200, LIST_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_1_FIXTURE));
        scripted.add(new Scripted(200, DETAIL_2_FIXTURE));

        PlatformConnectionTestResult result = new OzonConnectionTester(api(1)).test(credential());

        assertTrue(result.success(), result.message());
        assertEquals(PlatformAuthType.OZON.name(), result.adapterType());
        assertEquals(1, recorded.size(), "limit=1 的探测不该触发任何明细回取");
        assertFalse(result.message().contains(API_KEY), "回显不得含 Api-Key: " + result.message());
        assertFalse(result.message().contains(CLIENT_ID), "回显不得含 Client-Id: " + result.message());
        assertTrue(result.message().contains("127.0.0.1"), result.message());

        Recorded probe = recorded.get(0);
        assertEquals(OzonSellerApi.ORDERS_LIST_PATH, probe.path);
        Map<String, Object> filter = PlatformJson.nested(PlatformJson.object(probe.body), "filter");
        assertEquals(1, filter.get("limit"), "最小代价探测");
        assertTrue(OFFSET_TIMESTAMP.matcher(String.valueOf(filter.get("since"))).matches(),
                "§8：探测调用的时间同样必须带偏移 -> " + filter.get("since"));
        assertTrue(OFFSET_TIMESTAMP.matcher(String.valueOf(filter.get("until"))).matches(),
                "§8：探测调用的时间同样必须带偏移 -> " + filter.get("until"));
        assertEquals(CLIENT_ID, probe.header(OzonSellerApi.CLIENT_ID_HEADER));
        assertEquals(API_KEY, probe.header(OzonSellerApi.API_KEY_HEADER));
    }

    @Test
    void shouldReturnFailureReasonInsteadOfThrowingWhenCredentialIsRejected() {
        scripted.add(new Scripted(401, "{\"code\":0,\"message\":\"api key is invalid\"}"));

        PlatformConnectionTestResult result = new OzonConnectionTester(api(1)).test(credential());

        assertFalse(result.success());
        assertTrue(result.message().contains("凭据"), result.message());
        assertFalse(result.message().contains(API_KEY), result.message());
    }

    @Test
    void shouldReportMissingCredentialAsFailureInsteadOfThrowing() {
        PlatformCredential noApiKey = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                storeUrl, CLIENT_ID, null, null, null, null, null, null, null, null);

        PlatformConnectionTestResult result = new OzonConnectionTester(api(1)).test(noApiKey);

        assertFalse(result.success());
        assertTrue(result.message().contains("Api-Key"), result.message());
        assertFalse(result.message().contains(CLIENT_ID), "失败消息也不该回显另一把凭据: " + result.message());
        assertTrue(recorded.isEmpty(), "凭据不全就不该出网");
    }

    // ---------- 测试装置 ----------

    private OzonOrderFetcher fetcher() {
        return new OzonOrderFetcher(api(20));
    }

    private OzonSellerApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new OzonSellerApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON", storeUrl,
                CLIENT_ID, API_KEY, null, null, null, null, null, null, null);
    }

    private static PlatformOrderFetchRequest orderRequest(LocalDateTime from, LocalDateTime to, Integer limit) {
        return new PlatformOrderFetchRequest("tenant-1", "OZON", null, null, null, null, from, to, limit, 1);
    }

    /**
     * 递归收集请求体里所有「看起来像时间戳」的字符串值，凡是不带时区偏移的都记进 offenders。
     */
    private static void collectDateTimeLikeValues(Object node, String path, List<String> offenders) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> collectDateTimeLikeValues(value, path + "." + key, offenders));
            return;
        }
        if (node instanceof List<?> list) {
            for (Object element : list) {
                collectDateTimeLikeValues(element, path, offenders);
            }
            return;
        }
        if (node instanceof String text && text.matches("^\\d{4}-\\d{2}-\\d{2}T.*")) {
            if (!OFFSET_TIMESTAMP.matcher(text).matches()) {
                offenders.add(path + "=" + text);
            }
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, LIST_NO_MORE_FIXTURE) : scripted.poll();
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
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Scripted(int status, String body) {
    }
}
