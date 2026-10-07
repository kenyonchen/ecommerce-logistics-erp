package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ozon Seller API 传输层的鉴权头与 host 约束契约测试。
 * <p>
 * 断言的全是 §8（docs/spec/platform-api-reference.md）写死的形状：{@code Client-Id}+{@code Api-Key}
 * 两个静态头（缺一即 401）、只有 {@code https://api-seller.ozon.ru} 且强制 https、
 * 没有 OAuth 也没有签名（所以 {@code Authorization}/{@code X-API-Key} 必须不出现），
 * 以及 §8 的 {@code filter.limit} 上限 1000 与属性接口默认 100 的夹取口径。
 * 凭据值在任何异常消息里都不许出现（BR-01/BR-02）。
 *
 * @author kenyon
 */
class OzonSellerApiContractTest {

    private static final String CLIENT_ID = "215000371";
    private static final String API_KEY = "secret-api-key-never-echoed";

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
    void shouldSendClientIdAndApiKeyAndNoBearerOrCustomShopHeader() {
        scripted.add(new Scripted(200, "{\"result\":{\"posts\":[]}}"));

        api().postJson(credential(CLIENT_ID, API_KEY), OzonSellerApi.ORDERS_LIST_PATH, Map.of("probe", 1));

        Recorded request = recorded.get(0);
        assertEquals("POST", request.method);
        assertEquals(OzonSellerApi.ORDERS_LIST_PATH, request.path);
        assertEquals(CLIENT_ID, request.header(OzonSellerApi.CLIENT_ID_HEADER));
        assertEquals(API_KEY, request.header(OzonSellerApi.API_KEY_HEADER));
        assertFalse(request.headers.containsKey("authorization"), "§8：Ozon 没有 OAuth/Bearer，不该发 Authorization");
        assertFalse(request.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头，Ozon 不用");
        assertFalse(request.headers.containsKey("x-shopify-access-token"));
        assertTrue(String.valueOf(request.header("content-type")).startsWith("application/json"),
                "请求体是 JSON: " + request.header("content-type"));
        assertEquals("{\"probe\":1}", request.body);
    }

    @Test
    void shouldNameTheMissingCredentialFieldWithoutLeakingTheOtherValue() {
        BusinessException noClientId = assertThrows(BusinessException.class,
                () -> api().postJson(credential(null, API_KEY), OzonSellerApi.ORDERS_LIST_PATH, Map.of()));
        BusinessException noApiKey = assertThrows(BusinessException.class,
                () -> api().postJson(credential(CLIENT_ID, "  "), OzonSellerApi.ORDERS_LIST_PATH, Map.of()));
        BusinessException noCredential = assertThrows(BusinessException.class,
                () -> api().postJson(null, OzonSellerApi.ORDERS_LIST_PATH, Map.of()));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", noClientId.getErrorCode());
        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", noApiKey.getErrorCode());
        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", noCredential.getErrorCode());
        assertTrue(noClientId.getMessage().contains("apiKey(Client-Id)"), noClientId.getMessage());
        assertTrue(noApiKey.getMessage().contains("apiSecret(Api-Key)"), noApiKey.getMessage());
        assertFalse(noApiKey.getMessage().contains(API_KEY), "异常消息不得回显凭据值(BR-02): " + noApiKey.getMessage());
        assertTrue(recorded.isEmpty(), "凭据不全就不该出网");
    }

    // ---------- host 与 https ----------

    @Test
    void shouldUseTheOnlyVerifiedProductionHostWhenNoOverrideExists() {
        assertEquals("https://api-seller.ozon.ru", OzonSellerApi.DEFAULT_BASE);
        PlatformCredential noStoreUrl = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                null, CLIENT_ID, API_KEY, null, null, null, null, null, null, null);

        assertEquals("https://api-seller.ozon.ru", api().base(noStoreUrl));
        assertEquals(URI.create("https://api-seller.ozon.ru/v3/posting/fbs/list"),
                api().uri(noStoreUrl, OzonSellerApi.ORDERS_LIST_PATH));
    }

    @Test
    void shouldRejectPlainHttpOverrideInsteadOfSilentlyUsingIt() {
        PlatformCredential insecure = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                "http://api-seller.ozon.ru", CLIENT_ID, API_KEY, null, null, null, null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> api().base(insecure));

        assertEquals("PLATFORM_STORE_URL_INSECURE", error.getErrorCode());
        assertTrue(error.getMessage().contains("https"), error.getMessage());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldKeepHttpsOverrideWithPrefixAndNormaliseSchemelessOverride() {
        PlatformCredential prefixed = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                "https://proxy.example.internal/ozon/", CLIENT_ID, API_KEY, null, null, null, null, null, null, null);
        PlatformCredential schemeless = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                "api-seller.ozon.ru", CLIENT_ID, API_KEY, null, null, null, null, null, null, null);

        assertEquals("https://proxy.example.internal/ozon", api().base(prefixed));
        assertEquals("https://api-seller.ozon.ru", api().base(schemeless));
        assertEquals("api-seller.ozon.ru", api().host(schemeless));
    }

    @Test
    void shouldRejectOverrideWithoutHost() {
        PlatformCredential broken = new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON",
                "https://", CLIENT_ID, API_KEY, null, null, null, null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> api().base(broken));

        assertEquals("PLATFORM_STORE_URL_INVALID", error.getErrorCode());
    }

    // ---------- 夹取 ----------

    @Test
    void shouldClampLimitsToTheVerifiedCeilings() {
        // §8：订单列表 filter.limit 上限 1000
        assertEquals(1000, OzonSellerApi.clampListLimit(5000));
        assertEquals(1000, OzonSellerApi.clampListLimit(null));
        assertEquals(25, OzonSellerApi.clampListLimit(25));
        // §8：商品属性 limit 上限 1000、默认 100
        assertEquals(1000, OzonSellerApi.clampAttributesLimit(9999));
        assertEquals(100, OzonSellerApi.clampAttributesLimit(null));
        assertEquals(100, OzonSellerApi.clampAttributesLimit(0));
        assertEquals(37, OzonSellerApi.clampAttributesLimit(37));
    }

    @Test
    void shouldRegisterUnderTheOzonProtocolKey() {
        assertEquals(PlatformAuthType.OZON.name(), new OzonOrderFetcher(api()).type());
        assertEquals(PlatformAuthType.OZON.name(), new OzonProductFetcher(api()).type());
        assertEquals(PlatformAuthType.OZON.name(), new OzonFulfillmentPusher(api()).type());
        assertEquals(PlatformAuthType.OZON.name(), new OzonConnectionTester(api()).type());
    }

    // ---------- 测试装置 ----------

    private OzonSellerApi api() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：要验证退避路径，但契约测试不能真的睡很久
        return new OzonSellerApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)), 20);
    }

    private PlatformCredential credential(String clientId, String apiKey) {
        return new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON", storeUrl,
                clientId, apiKey, null, null, null, null, null, null, null);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, "{\"result\":{\"posts\":[]}}") : scripted.poll();
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
