package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Magento 传输层与令牌获取的契约测试：URL 形态、store view、Bearer 头，以及这一家特有的
 * 「令牌按实例自取」语义。
 * <p>
 * 断言的都是 §9（docs/spec/platform-api-reference.md）写死的形状：
 * {@code https://<host>/rest/<store-view-code>/<V1 端点>} 且 store-view-code 缺省 {@code default}、
 * 换令牌是 {@code PUT /rest/V1/integration/admin/token}（<b>不带</b> store-view-code）且
 * <b>成功响应是裸 JSON 字符串</b>而不是对象、{@code MAGENTO} 不在 {@code oauthTokenBased()} 里
 * 所以共享刷新层不跑、必须由适配器自己拿令牌。
 * 另有两条纪律级断言：粘贴 {@code accessToken} 时<b>一次换令牌调用都不发</b>；
 * {@code X-API-Key} 这类别的协议的约定头不得出现；凭据值不得进日志/异常（BR-01/BR-02）。
 *
 * @author kenyon
 */
class MagentoRestApiContractTest {

    private static final String ADMIN_USER = "erp_sync";
    private static final String ADMIN_PASSWORD = "admin-pass-never-echoed";
    private static final String PASTED_TOKEN = "pasted-integration-token-never-echoed";
    private static final String ISSUED_TOKEN = "asdf3hjklp5iuytre";
    private static final String EMPTY_ORDERS = "{\"items\":[],\"total_count\":0}";
    private static final String ORDERS_PAGE_ONE =
            "{\"items\":[{\"entity_id\":101,\"status\":\"processing\"},{\"entity_id\":102,\"status\":\"new\"}],"
                    + "\"search_criteria\":[],\"total_count\":4}";
    private static final String ORDERS_PAGE_TWO =
            "{\"items\":[{\"entity_id\":103,\"status\":\"pending\"},{\"entity_id\":104,\"status\":\"complete\"}],"
                    + "\"search_criteria\":[],\"total_count\":4}";

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

    // ---------- 令牌来源 ----------

    @Test
    void shouldNotCallTheTokenEndpointWhenAnIntegrationTokenIsPasted() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        new MagentoOrderFetcher(api()).fetch(orderRequest(10), credential(PASTED_TOKEN));

        assertEquals(1, recorded.size(), "§9 的 SaaS 口径是直接粘贴集成令牌，填了就不该再换取");
        Recorded request = recorded.get(0);
        assertEquals("GET", request.method);
        assertEquals("Bearer " + PASTED_TOKEN, request.header("authorization"));
        assertFalse(request.path.contains("integration/admin/token"), request.path);
    }

    @Test
    void shouldExchangeAdminCredentialsOnceAndParseTheBareStringToken() {
        // §9：换令牌的成功响应是一段裸 JSON 字符串，不是 JSON 对象
        scripted.add(new Scripted(200, "\"" + ISSUED_TOKEN + "\""));
        // 两页都有条目且 total_count 还没被消费完，确保真的发生第二次 GET
        scripted.add(new Scripted(200, ORDERS_PAGE_ONE));
        scripted.add(new Scripted(200, ORDERS_PAGE_TWO));

        // 换令牌只应发生一次（本次调用内的 memo），而不是每发请求换一次
        List<Map<String, Object>> rows = new MagentoOrderFetcher(api(2)).fetch(orderRequest(10), credential(null));

        assertEquals(4, rows.size(), rows.toString());
        assertEquals(3, recorded.size(), recorded.toString());
        Recorded token = recorded.get(0);
        assertEquals("PUT", token.method);
        assertEquals("/rest/V1/integration/admin/token", token.path,
                "§9 的换令牌端点不带 store-view-code，且是 PUT");
        assertEquals("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}", token.body);
        assertEquals(1, countByMethod("PUT"), "一次适配器调用只换一次令牌");

        Recorded first = recorded.get(1);
        Recorded second = recorded.get(2);
        assertEquals("Bearer " + ISSUED_TOKEN, first.header("authorization"));
        assertEquals("Bearer " + ISSUED_TOKEN, second.header("authorization"),
                "第二页复用同一令牌，且不回显口令");
        String firstQuery = decode(first.query);
        String secondQuery = decode(second.query);
        assertTrue(firstQuery.contains("searchCriteria[currentPage]=1"), firstQuery);
        assertTrue(secondQuery.contains("searchCriteria[currentPage]=2"), secondQuery);
    }

    @Test
    void shouldRequireATokenSourceAndNameAllThreeFieldsWithoutEchoingThem() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> new MagentoOrderFetcher(api()).fetch(orderRequest(5), credential(null, null, null)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("accessToken"), error.getMessage());
        assertTrue(error.getMessage().contains("apiKey"), error.getMessage());
        assertTrue(error.getMessage().contains("apiSecret"), error.getMessage());
        assertFalse(error.getMessage().contains(ADMIN_PASSWORD), "异常消息不得回显口令: " + error.getMessage());
        assertTrue(recorded.isEmpty(), "没有令牌来源就不该出网");
    }

    @Test
    void shouldRejectAnObjectShapedTokenResponseInsteadOfTreatingItAsAToken() {
        scripted.add(new Scripted(200, "{\"message\":\"2FA required\"}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new MagentoOrderFetcher(api()).fetch(orderRequest(5), credential(null)));

        assertEquals("PLATFORM_RESPONSE_INVALID", error.getErrorCode());
        assertFalse(error.getMessage().contains("2FA required"),
                "令牌端点的响应体不回显，避免把令牌带进消息: " + error.getMessage());
        assertEquals(1, recorded.size(), "换令牌失败不该继续发订单请求");
    }

    @Test
    void shouldParseTheBareStringBodyAndRejectEmptyOrWrappedOnes() {
        assertEquals(ISSUED_TOKEN, MagentoAdminToken.bareToken("  \"" + ISSUED_TOKEN + "\"  "));
        assertEquals(ISSUED_TOKEN, MagentoAdminToken.bareToken(ISSUED_TOKEN), "无引号形态也认，不让格式差异卡死同步");
        assertThrows(BusinessException.class, () -> MagentoAdminToken.bareToken("   "));
        assertThrows(BusinessException.class, () -> MagentoAdminToken.bareToken("\"  \""));
        assertThrows(BusinessException.class, () -> MagentoAdminToken.bareToken("[\"a\"]"));
    }

    // ---------- URL 形态与 store view ----------

    @Test
    void shouldBuildTheRestUrlWithTheDefaultStoreViewWhenRegionIsBlank() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        api().get(api().ordersUri(credential(PASTED_TOKEN), Map.of()), api().tokenFor(credential(PASTED_TOKEN)));

        Recorded request = recorded.get(0);
        assertEquals("/rest/default/V1/orders", request.path, "§9：store-view-code 缺省即 default");
    }

    @Test
    void shouldHonourTheStoreViewCodeFromRegion() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));
        PlatformCredential credential = credential(PASTED_TOKEN, "website");

        api().get(api().ordersUri(credential, Map.of()), api().tokenFor(credential));

        assertEquals("/rest/website/V1/orders", recorded.get(0).path,
                "region 就是 store_view_code（PlatformAuthCatalog 的 MAGENTO 行）");
    }

    @Test
    void shouldRejectStoreViewCodesThatWouldRewriteThePath() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> api().storeView(credential(PASTED_TOKEN, "default/../admin")));

        assertEquals("PLATFORM_STORE_VIEW_INVALID", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldRequireStoreUrlBecauseMagentoHasNoBuiltInHost() {
        PlatformCredential noStoreUrl = new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO",
                null, ADMIN_USER, ADMIN_PASSWORD, null, null, null, null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> api().base(noStoreUrl));

        assertEquals("PLATFORM_STORE_URL_REQUIRED", error.getErrorCode(),
                "Magento 是自建软件，storeUrl 是唯一 host 来源，必须用专用码拒而不是退回内置 host");
    }

    @Test
    void shouldRejectANonHttpStoreUrlAndNormaliseTheRest() {
        assertEquals("https://shop.example.com", api().base(credentialWithStoreUrl("shop.example.com/")));
        assertEquals("http://127.0.0.1:8080", api().base(credentialWithStoreUrl("http://127.0.0.1:8080/")));
        assertEquals("shop.example.com", api().host(credentialWithStoreUrl("https://shop.example.com")));

        BusinessException ftp = assertThrows(BusinessException.class,
                () -> api().base(credentialWithStoreUrl("ftp://shop.example.com")));
        assertEquals("PLATFORM_STORE_URL_INVALID", ftp.getErrorCode());
        BusinessException schemeOnly = assertThrows(BusinessException.class,
                () -> api().base(credentialWithStoreUrl("https://")));
        assertEquals("PLATFORM_STORE_URL_INVALID", schemeOnly.getErrorCode());
    }

    @Test
    void shouldEncodeSearchCriteriaKeysAndValuesInsteadOfLeavingRawBrackets() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put(MagentoRestApi.PAGE_SIZE_KEY, "20");
        MagentoRestApi.putFilter(query, 0, 0, "name", "%tee%", "like");

        URI uri = api().ordersUri(credential(PASTED_TOKEN), query);

        assertFalse(uri.getRawQuery().contains("["), "数组语法要 percent-编码后才进 java.net.URI: " + uri);
        assertFalse(uri.getRawQuery().contains("%t"), "值里的 % 通配符必须编成 %25，否则是非法转义: " + uri);
        String decoded = java.net.URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("searchCriteria[pageSize]=20"), decoded);
        assertTrue(decoded.contains("[condition_type]=like"), decoded);
        assertTrue(decoded.contains("[value]=%tee%"), decoded);
    }

    @Test
    void shouldClampPageSizeToTheLocallyChosenCeiling() {
        // §9：pageSize 没有官方默认值也没有上限，所以只有一个本仓库自选的夹子
        assertEquals(MagentoRestApi.DEFAULT_PAGE_SIZE, MagentoRestApi.pageSize(null));
        assertEquals(MagentoRestApi.DEFAULT_PAGE_SIZE, MagentoRestApi.pageSize(5000));
        assertEquals(25, MagentoRestApi.pageSize(25));
    }

    // ---------- 头的形状 ----------

    @Test
    void shouldSendOnlyBearerAndNoneOfTheOtherProtocolsHeaders() {
        scripted.add(new Scripted(200, EMPTY_ORDERS));

        new MagentoOrderFetcher(api()).fetch(orderRequest(5), credential(PASTED_TOKEN));

        Recorded request = recorded.get(0);
        assertEquals("Bearer " + PASTED_TOKEN, request.header("authorization"),
                "§9 唯一的鉴权形状是 Bearer，且只有一个");
        assertTrue(request.headers.keySet().stream()
                        .noneMatch(name -> name.contains("key") || name.contains("secret")
                                || name.contains("shopify") || name.contains("client")),
                "别的协议的鉴权约定不得混进来: " + request.headers.keySet());
        assertFalse(request.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头，Magento 不用");
        assertFalse(request.headers.containsKey("x-api-secret"));
        assertFalse(request.headers.containsKey("client-id"), "Client-Id/Api-Key 是 Ozon 的头");
        assertFalse(request.headers.containsKey("x-shopify-access-token"));
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutLeakingTheToken() {
        scripted.add(new Scripted(401, "{\"message\":\"The consumer isn't authorized\"}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new MagentoOrderFetcher(api()).fetch(orderRequest(5), credential(PASTED_TOKEN)));

        assertEquals(1, recorded.size(), "令牌被拒重试没有意义");
        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains(PASTED_TOKEN), "异常消息不得回显令牌: " + error.getMessage());
        assertFalse(error.getMessage().contains(ADMIN_PASSWORD), error.getMessage());
    }

    // ---------- 注册键与连通性测试 ----------

    @Test
    void shouldRegisterUnderTheMagentoProtocolKey() {
        MagentoRestApi api = api();
        assertEquals(PlatformAuthType.MAGENTO.name(), new MagentoOrderFetcher(api).type());
        assertEquals(PlatformAuthType.MAGENTO.name(), new MagentoProductFetcher(api).type());
        assertEquals(PlatformAuthType.MAGENTO.name(), new MagentoFulfillmentPusher(api).type());
        assertEquals(PlatformAuthType.MAGENTO.name(), new MagentoConnectionTester(api).type());
        assertFalse(PlatformAuthType.MAGENTO.oauthTokenBased(),
                "这一家不在共享令牌刷新清单里，适配器必须自己拿令牌");
    }

    @Test
    void shouldProbeWithTheCheapestAuthenticatedReadAndHideCredentials() {
        scripted.add(new Scripted(200, "{\"items\":[],\"total_count\":0}"));

        PlatformConnectionTestResult result = new MagentoConnectionTester(api()).test(credential(PASTED_TOKEN));

        assertTrue(result.success(), result.message());
        assertEquals(PlatformAuthType.MAGENTO.name(), result.adapterType());
        assertEquals("GET", recorded.get(0).method);
        assertEquals("/rest/default/V1/orders", recorded.get(0).path);
        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("searchCriteria[pageSize]=1"), "最小代价: " + query);
        assertTrue(result.message().contains("127.0.0.1"), "回显要说明打到了哪个 host: " + result.message());
        assertTrue(result.message().contains("default"), "回显要说明用了哪个 store view: " + result.message());
        assertFalse(result.message().contains(PASTED_TOKEN), "回显不得含令牌: " + result.message());
    }

    @Test
    void shouldReportMissingStoreUrlAsFailureInsteadOfThrowing() {
        PlatformCredential noStoreUrl = new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO",
                "  ", ADMIN_USER, ADMIN_PASSWORD, null, null, null, null, null, null, null);

        PlatformConnectionTestResult result = new MagentoConnectionTester(api()).test(noStoreUrl);

        assertFalse(result.success());
        assertTrue(result.message().contains("storeUrl"), result.message());
        assertTrue(recorded.isEmpty(), "base URL 都没有就不该出网");
    }

    // ---------- 测试装置 ----------

    private MagentoRestApi api() {
        return api(20);
    }

    private MagentoRestApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：要验证 429 退避路径，但契约测试不能真的睡很久
        return new MagentoRestApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential(String accessToken) {
        return credential(accessToken, null);
    }

    private PlatformCredential credential(String accessToken, String region) {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                ADMIN_USER, ADMIN_PASSWORD, accessToken, null, null, null, region, null, null);
    }

    private PlatformCredential credential(String accessToken, String apiKey, String apiSecret) {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                apiKey, apiSecret, accessToken, null, null, null, null, null, null);
    }

    private PlatformCredential credentialWithStoreUrl(String url) {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", url,
                ADMIN_USER, ADMIN_PASSWORD, PASTED_TOKEN, null, null, null, null, null, null);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "MAGENTO", null, null, null, null, null, null, limit, 1);
    }

    private int countByMethod(String method) {
        int matched = 0;
        for (Recorded entry : recorded) {
            if (method.equals(entry.method)) {
                matched++;
            }
        }
        return matched;
    }

    private static String decode(String rawUriWithQuery) {
        return java.net.URLDecoder.decode(rawUriWithQuery, StandardCharsets.UTF_8);
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
