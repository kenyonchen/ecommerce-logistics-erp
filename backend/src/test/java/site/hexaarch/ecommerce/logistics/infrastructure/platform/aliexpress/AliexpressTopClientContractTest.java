package site.hexaarch.ecommerce.logistics.infrastructure.platform.aliexpress;

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
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformSignatures;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AliexpressTopClient} 的请求形状契约测试：TOP 公共参数、可复算的 md5 大写签名、POST 表单传输、
 * §5.1 信封解包与 {@code error_response} 语义。
 * <p>
 * 断言的是「我们怎么调 TOP 网关」（§5.1）：公共参数齐全、{@code sign} 等于按 §5.1 口径独立复算的摘要、
 * {@code sign} 不进拼接串、正式调用是 {@code application/x-www-form-urlencoded} 的 POST、
 * {@code timestamp} 是 {@code yyyy-MM-dd HH:mm:ss}、{@code error_response} 的 {@code sub_code} 进了异常消息。
 * <b>凭据/签名值不出现在异常或消息里</b>（BR-01/BR-02）。
 *
 * @author kenyon
 */
class AliexpressTopClientContractTest {

    private static final String APP_KEY = "1234567";
    private static final String APP_SECRET = "top_app_secret_live";
    private static final String SESSION = "sess_5001_abcdef";

    private static final String LIST_OK = """
            {"aliexpress_trade_seller_orderlist_get_response":{"total_page":1,"orders":[]}}""";

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

    @Test
    void shouldPostFormUrlEncodedWithAllTopCommonParams() {
        scripted.add(new Scripted(200, LIST_OK));

        client().execute(credential(), AliexpressTopClient.ORDER_LIST_METHOD, business());

        Recorded sent = recorded.get(0);
        assertEquals("POST", sent.method, "§5.1：正式调用必须 POST，GET 仅调试");
        assertTrue(sent.contentType.startsWith("application/x-www-form-urlencoded"),
                "§5.1：业务参数 URL 编码放 body: " + sent.contentType);
        Map<String, String> form = parseForm(sent.body);
        for (String key : List.of("method", "app_key", "session", "timestamp", "v", "sign_method", "sign", "format")) {
            assertTrue(form.containsKey(key), "§5.1 公共参数缺失 " + key + ": " + form.keySet());
        }
        assertEquals(AliexpressTopClient.ORDER_LIST_METHOD, form.get("method"));
        assertEquals(APP_KEY, form.get("app_key"));
        assertEquals(SESSION, form.get("session"));
        assertEquals("2.0", form.get("v"));
        assertEquals("md5", form.get("sign_method"));
        assertEquals("json", form.get("format"));
        assertEquals("/router/rest", sent.path, "storeUrl 只填 host 时应补上 §5 记录的端点路径");
    }

    @Test
    void shouldTimestampAsYyyyMmDdSpaceColonFormat() {
        scripted.add(new Scripted(200, LIST_OK));

        client().execute(credential(), AliexpressTopClient.ORDER_LIST_METHOD, business());

        String timestamp = parseForm(recorded.get(0).body).get("timestamp");
        assertTrue(timestamp != null && timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "§5.1：timestamp 格式 yyyy-MM-dd HH:mm:ss，实际=" + timestamp);
    }

    @Test
    void shouldSendSignRecomputableFromThePostedFormExcludingSignItself() {
        scripted.add(new Scripted(200, LIST_OK));

        client().execute(credential(), AliexpressTopClient.ORDER_LIST_METHOD, business());

        Map<String, String> form = parseForm(recorded.get(0).body);
        String sentSign = form.get("sign");
        // 独立复算：对同一份被发送的参数（sign 自身不参与）按 §5.1 的 md5 口径再签一次
        String expected = PlatformSignatures.topSign(form, APP_SECRET, "md5");
        assertEquals(sentSign, expected, "sign 必须能按发送参数复算，否则网关会判 error_signature");
        assertFalse(PlatformSignatures.topConcat(form, "sign").contains(sentSign),
                "sign 值不能出现在参与拼接的串里");
    }

    @Test
    void shouldMatchHandComputedMd5UpperDigestForFixedInput() {
        Map<String, String> fixed = new LinkedHashMap<>();
        fixed.put("app_key", "1234567");
        fixed.put("v", "2.0");
        fixed.put("sign_method", "md5");
        fixed.put("timestamp", "2026-01-25 12:00:00");
        fixed.put("format", "json");
        fixed.put("method", "aliexpress.trade.seller.orderlist.get");
        fixed.put("session", "test_session");
        fixed.put("param_aeop_order_query", "{\"current_page\":1,\"page_size\":20}");

        String sign = PlatformSignatures.topSign(fixed, "test_secret", "md5");

        // md5(secret + 字典序k+v + secret) 取 32 位大写；期望值按该规则离线独立算出后硬编码，
        // 一旦有人改成不包 secret、小写、或换了拼接顺序都会立刻红。
        // 字典序参与拼接的键为 app_key/format/method/param_aeop_order_query/session/sign_method/timestamp/v
        assertEquals("7BF1FAB37A6E9DD340672327A852F122", sign);
        assertTrue(sign.matches("[0-9A-F]{32}"), "§5.1：md5 签名是 32 位大写 hex，实际=" + sign);
    }

    @Test
    void shouldExcludeTheSignParameterFromTheConcatenatedString() {
        Map<String, String> without = new LinkedHashMap<>();
        without.put("app_key", "1234567");
        without.put("method", "aliexpress.trade.seller.orderlist.get");
        without.put("v", "2.0");
        Map<String, String> with = new LinkedHashMap<>(without);
        with.put("sign", "DEADBEEF");

        // 加入名为 sign 的参数不应改变拼接串——它被排除（§5.1「除去sign参数」）
        assertEquals(PlatformSignatures.topConcat(without, "sign"), PlatformSignatures.topConcat(with, "sign"));
    }

    @Test
    void shouldSurfaceSubCodeInExceptionWhenGatewayReturnsErrorResponse() {
        scripted.add(new Scripted(200, """
                {"error_response":{"msg":"Remote service error","code":50,
                "sub_msg":"非法参数","sub_code":"isv.invalid-parameter:current_page"}}"""));

        BusinessException error = assertThrows(BusinessException.class,
                () -> client().execute(credential(), AliexpressTopClient.ORDER_LIST_METHOD, business()));

        assertEquals("PLATFORM_TOP_ERROR", error.getErrorCode());
        assertTrue(error.getMessage().contains("isv.invalid-parameter"), error.getMessage());
        assertTrue(error.getMessage().contains("sub_code"), error.getMessage());
        assertFalse(error.getMessage().contains(APP_SECRET), "异常不得回显 app_secret: " + error.getMessage());
        assertFalse(error.getMessage().contains(SESSION), "异常不得回显 session: " + error.getMessage());
    }

    @Test
    void shouldGuardMissingAppSecretAndSessionNamingTheFieldWithoutEchoingValue() {
        PlatformCredential noSecret = new PlatformCredential(PlatformAuthType.ALIEXPRESS, "t", "ALIEXPRESS",
                storeUrl, APP_KEY, " ", SESSION, null, null, null, null, null, null);
        BusinessException secretError = assertThrows(BusinessException.class,
                () -> client().execute(noSecret, AliexpressTopClient.ORDER_LIST_METHOD, business()));
        assertTrue(secretError.getMessage().contains("apiSecret"), secretError.getMessage());
        assertFalse(secretError.getMessage().contains(SESSION), secretError.getMessage());

        recorded.clear();
        PlatformCredential noSession = new PlatformCredential(PlatformAuthType.ALIEXPRESS, "t", "ALIEXPRESS",
                storeUrl, APP_KEY, APP_SECRET, "  ", null, null, null, null, null, null);
        BusinessException sessionError = assertThrows(BusinessException.class,
                () -> client().execute(noSession, AliexpressTopClient.ORDER_LIST_METHOD, business()));
        assertEquals("PLATFORM_SESSION_REQUIRED", sessionError.getErrorCode());
        assertTrue(sessionError.getMessage().contains("accessToken"), sessionError.getMessage());
        assertTrue(sessionError.getMessage().contains("session"), sessionError.getMessage());
        assertTrue(recorded.isEmpty(), "缺凭据字段就不该发出请求");
    }

    @Test
    void shouldNeverPutCredentialsInHeadersOrSendCustomShopApiKey() {
        scripted.add(new Scripted(200, LIST_OK));

        client().execute(credential(), AliexpressTopClient.ORDER_LIST_METHOD, business());

        Recorded sent = recorded.get(0);
        assertFalse(sent.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 约定，不该出现在 TOP");
        assertFalse(sent.headers.containsKey("x-api-secret"));
        assertFalse(sent.headers.containsKey("authorization"), "TOP 用签名鉴权，没有 Bearer 头");
        String form = parseForm(sent.body).get("sign");
        assertFalse(sent.headers.containsValue(APP_SECRET), "app_secret 不进头");
        assertTrue(form != null && !form.isBlank());
    }

    // ---------- 测试装置 ----------

    private AliexpressTopClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new AliexpressTopClient(
                new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)), "md5", 20);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.ALIEXPRESS, "tenant-1", "ALIEXPRESS", storeUrl,
                APP_KEY, APP_SECRET, SESSION, null, null, null, null, null, null);
    }

    private static Map<String, String> business() {
        Map<String, String> business = new LinkedHashMap<>();
        business.put("param_aeop_order_query", "{\"current_page\":1,\"page_size\":20}");
        return business;
    }

    private static Map<String, String> parseForm(String rawBody) {
        Map<String, String> form = new LinkedHashMap<>();
        if (rawBody == null || rawBody.isBlank()) {
            return form;
        }
        for (String pair : rawBody.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                form.put(key, value);
            }
        }
        return form;
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        entry.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, LIST_OK) : scripted.poll();
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
        private String contentType;
        private final Map<String, String> headers = new LinkedHashMap<>();
    }

    private record Scripted(int status, String body) {
    }
}
