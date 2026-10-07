package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

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
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformOAuthClient;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformToken;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Amazon SP-API 传输层与 LWA 令牌交换的契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，断言的是「我们怎么调用 SP-API」这一层协议事实：鉴权头是
 * {@code x-amz-access-token} 且没有签名/Authorization 头、区域到 host 的映射、{@code storeUrl} 作为
 * 绝对 base（含沙箱 {@code /sandbox} 路径前缀）的覆盖语义、令牌端点的表单形状，以及凭据缺失时
 * 在发出任何请求之前就失败。<b>响应体形状未经真实平台验证</b>（当前无沙箱凭据），上线前仍需凭据演练。
 *
 * @author kenyon
 */
class AmazonSpApiContractTest {

    private static final String ACCESS_TOKEN = "atk_test";
    private static final String REFRESH_TOKEN = "rt_test_refresh";
    private static final String CLIENT_ID = "amzn1.client_id_test";
    private static final String CLIENT_SECRET = "client_secret_test";
    private static final String MARKETPLACE_US = "ATVPDKIKX0DER";

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
    void shouldAuthenticateWithAccessTokenHeaderAndNoSigningHeaders() {
        scripted.add(new Scripted(200, "{\"payload\":{\"Orders\":[]}}"));

        new AmazonOrderFetcher(api(3)).fetch(orderRequest(5), credential(storeUrl, "NA", MARKETPLACE_US));

        Recorded request = recorded.get(0);
        assertEquals("GET", request.method);
        assertEquals("/orders/v0/orders", request.path);
        assertEquals(ACCESS_TOKEN, request.header(AmazonSpApi.ACCESS_TOKEN_HEADER));
        assertFalse(request.headers.containsKey("x-api-key"), "不得沿用自建店铺的 X-API-Key 约定");
        assertFalse(request.headers.containsKey("x-api-secret"));
        assertFalse(request.headers.containsKey("authorization"), "§3.2：SP-API 没有 SigV4 步骤，不发 Authorization 头");
        assertFalse(request.headers.containsKey("x-amz-date"), "没有 SigV4 就不该出现 AWS 签名相关头");
        assertFalse(request.headers.containsKey("x-amz-content-sha256"));
    }

    @Test
    void shouldFailBeforeAnyRequestWhenAccessTokenMissing() {
        PlatformCredential tokenless = new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1", "AMAZON",
                storeUrl, CLIENT_ID, CLIENT_SECRET, null, null, null, REFRESH_TOKEN, "NA", null, MARKETPLACE_US);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new AmazonOrderFetcher(api(3)).fetch(orderRequest(5), tokenless));

        assertEquals("PLATFORM_ACCESS_TOKEN_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("refreshToken"), error.getMessage());
        assertTrue(recorded.isEmpty(), "令牌缺失说明令牌交换没跑过，不能发出注定 401 的请求白耗配额");
    }

    // ---------- host 选择 ----------

    @Test
    void shouldMapRegionToTheThreeIndependentHosts() {
        AmazonSpApi api = api(1);

        assertTrue(ordersPathOf(api, null, "NA").startsWith("https://sellingpartnerapi-na.amazon.com"),
                "NA 区域 host 不对");
        assertTrue(ordersPathOf(api, null, "EU").startsWith("https://sellingpartnerapi-eu.amazon.com"),
                "EU 区域 host 不对");
        assertTrue(ordersPathOf(api, null, "FE").startsWith("https://sellingpartnerapi-fe.amazon.com"),
                "FE 区域 host 不对");
        assertTrue(ordersPathOf(api, null, null).startsWith("https://sellingpartnerapi-na.amazon.com"),
                "region 空白要按 NA 兜底而不是不发 host");
        assertTrue(ordersPathOf(api, null, " eu ").startsWith("https://sellingpartnerapi-eu.amazon.com"),
                "region 大小写与空格不应影响选择");
    }

    @Test
    void shouldRejectUnknownRegionInsteadOfSilentlyFallingBack() {
        AmazonSpApi api = api(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> api.ordersUri(credential(null, "SG", MARKETPLACE_US), Map.of()));

        assertEquals("PLATFORM_REGION_UNSUPPORTED", error.getErrorCode());
        assertTrue(error.getMessage().contains("NA/EU/FE"), error.getMessage());
    }

    @Test
    void shouldUseStoreUrlAsAbsoluteBaseKeepingSandboxPathPrefix() {
        scripted.add(new Scripted(200, "{\"payload\":{\"Orders\":[]}}"));

        new AmazonOrderFetcher(api(3)).fetch(orderRequest(5), credential(storeUrl + "/sandbox", "NA", MARKETPLACE_US));

        assertEquals("/sandbox/orders/v0/orders", recorded.get(0).path,
                "§3.1：沙箱是同 host 上的 /sandbox 路径前缀，不是 sandbox. 前缀的域名");
    }

    @Test
    void shouldRejectStoreUrlOverrideWithoutSchemeOrHost() {
        AmazonSpApi api = api(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> api.ordersUri(credential("sellingpartnerapi-na.amazon.com", "NA", MARKETPLACE_US), Map.of()));

        assertEquals("PLATFORM_STORE_URL_INVALID", error.getErrorCode());
    }

    // ---------- MarketplaceIds ----------

    @Test
    void shouldRequireMarketplaceIdsAndNameTheField() {
        AmazonOrderFetcher fetcher = new AmazonOrderFetcher(api(3));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher.fetch(orderRequest(5), credential(storeUrl, "NA", null)));

        assertEquals("PLATFORM_MARKETPLACE_IDS_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("MarketplaceIds"), error.getMessage());
        assertTrue(error.getMessage().contains("marketplaceIds"), "消息要点名集成里该填的字段: " + error.getMessage());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldJoinAndTrimMultipleMarketplaceIds() {
        scripted.add(new Scripted(200, "{\"payload\":{\"Orders\":[]}}"));

        new AmazonOrderFetcher(api(3)).fetch(orderRequest(5),
                credential(storeUrl, "NA", MARKETPLACE_US + ", A2VIGQ35RCS4UG"));

        String query = decode(recorded.get(0).query);
        assertTrue(query.contains("MarketplaceIds=" + MARKETPLACE_US + ",A2VIGQ35RCS4UG"), query);
    }

    // ---------- 令牌交换 ----------

    @Test
    void shouldUseOfficialLwaEndpointLiteralNotTheLegacyAuthO2Host() {
        assertEquals("https://www.amazon.com/ap/oath/v1/token", AmazonLwaTokenExchange.DEFAULT_TOKEN_URI);
    }

    @Test
    void shouldExchangeRefreshTokenWithFormEncodedBodyCarryingFourParams() {
        scripted.add(new Scripted(200, "{\"access_token\":\"" + ACCESS_TOKEN
                + "\",\"token_type\":\"bearer\",\"expires_in\":3600}"));
        AmazonLwaTokenExchange exchange =
                new AmazonLwaTokenExchange(new PlatformOAuthClient(httpExchange()), storeUrl + "/ap/oath/v1/token");

        PlatformToken token = exchange.exchange(credential(storeUrl, "NA", MARKETPLACE_US));

        assertEquals(ACCESS_TOKEN, token.accessToken());
        assertNull(token.refreshToken(), "不轮换的平台不应凭空造出刷新令牌");
        assertFalse(exchange.rotatesRefreshToken(), "§2.2：Amazon 刷新令牌不会过期，回写等于覆盖掉长期凭据");
        assertEquals(PlatformAuthType.AMAZON_SP_API.name(), exchange.type());

        Recorded request = recorded.get(0);
        assertEquals("POST", request.method);
        assertEquals("/ap/oath/v1/token", request.path);
        assertTrue(String.valueOf(request.header("content-type")).startsWith("application/x-www-form-urlencoded"),
                "LWA 表单端点要求 urlencoded，不是 JSON: " + request.header("content-type"));
        Map<String, String> form = parseForm(request.body);
        assertEquals(4, form.size(), "§3.2 只给了四个表单参数: " + form.keySet());
        assertEquals("refresh_token", form.get("grant_type"));
        assertEquals(REFRESH_TOKEN, form.get("refresh_token"));
        assertEquals(CLIENT_ID, form.get("client_id"));
        assertEquals(CLIENT_SECRET, form.get("client_secret"));
        assertFalse(request.headers.containsKey("authorization"), "客户端凭据在表单体里，不是 Basic 头");
    }

    @Test
    void shouldRefuseToExchangeWhenCredentialFieldsMissingWithoutLeakingValues() {
        PlatformCredential noRefresh = new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1", "AMAZON",
                null, CLIENT_ID, CLIENT_SECRET, null, null, null, "  ", "NA", null, MARKETPLACE_US);
        PlatformCredential noSecret = new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1", "AMAZON",
                null, CLIENT_ID, null, null, null, null, REFRESH_TOKEN, "NA", null, MARKETPLACE_US);
        AmazonLwaTokenExchange exchange =
                new AmazonLwaTokenExchange(new PlatformOAuthClient(httpExchange()), storeUrl + "/ap/oath/v1/token");

        BusinessException missingRefresh = assertThrows(BusinessException.class, () -> exchange.exchange(noRefresh));
        BusinessException missingSecret = assertThrows(BusinessException.class, () -> exchange.exchange(noSecret));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", missingRefresh.getErrorCode());
        assertTrue(missingRefresh.getMessage().contains("refreshToken"), missingRefresh.getMessage());
        assertTrue(missingSecret.getMessage().contains("apiSecret"), missingSecret.getMessage());
        assertFalse(missingSecret.getMessage().contains(CLIENT_SECRET), "异常消息不得回显凭据值(BR-02)");
        assertFalse(missingRefresh.getMessage().contains(REFRESH_TOKEN), "异常消息不得回显凭据值(BR-02)");
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private static String ordersPathOf(AmazonSpApi api, String storeUrl, String region) {
        return api.ordersUri(credential(storeUrl, region, MARKETPLACE_US), Map.of()).toString();
    }

    private AmazonSpApi api(int maxPages) {
        return new AmazonSpApi(httpExchange(), maxPages);
    }

    private PlatformHttpExchange httpExchange() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：契约测试要验证退避逻辑，但不能真的睡很久
        return new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10));
    }

    private static PlatformCredential credential(String storeUrl, String region, String marketplaceIds) {
        return new PlatformCredential(PlatformAuthType.AMAZON_SP_API, "tenant-1", "AMAZON", storeUrl,
                CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, null, null, REFRESH_TOKEN, region, null, marketplaceIds);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "AMAZON", null, null, null,
                null, null, null, limit, 1);
    }

    private static String decode(String rawQuery) {
        return URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().toString();
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Scripted(int status, String body) {
    }
}
