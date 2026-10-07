package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformOAuthClient;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformToken;

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
 * Mercado Libre 令牌交换的契约测试：断言「我们怎么调用 {@code /oauth/token}」这一层协议事实。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，覆盖 docs/spec/platform-api-reference.md §7 与 §2.2 的四条口径：
 * 端点字面值、客户端凭据放 JSON body 而非 Basic 头、有效时长<b>只取响应的 {@code expires_in}</b>（不写死 6 小时）、
 * 刷新令牌一次性因此必须把轮换值带回去。另外用 Logback 的 {@link ListAppender} 证明令牌值既没进日志
 * 也没进异常消息（BR-02）——令牌交换是唯一会「顺手把响应打一行」的一环，这里必须按证据断言而不是靠约定。
 * <p>
 * 令牌响应的<b>字段名</b>按 §7 示例（{@code access_token}/{@code expires_in}/{@code refresh_token}）；
 * 尚未凭真实凭据演练过一次（ISS-064），故这里的 fixture 只证明我们的调用与解析形状，不证明平台侧结构。
 *
 * @author kenyon
 */
class MercadoTokenExchangeContractTest {

    private static final String ACCESS_TOKEN = "ATK_TEST_ACCESS_TOKEN";
    private static final String REFRESH_TOKEN = "RT_TEST_ONE_TIME_REFRESH";
    private static final String ROTATED_REFRESH_TOKEN = "RT_TEST_ROTATED_REFRESH";
    private static final String CLIENT_ID = "1357924680";
    private static final String CLIENT_SECRET = "TEST_CLIENT_SECRET";
    private static final String SELLER_USER_ID = "987654321";

    /**
     * §7 实测的错误信封原文（把 message 换成缺 client_secret 时的那种形状）
     */
    private static final String INVALID_CLIENT_FIXTURE =
            "{\"message\":\"invalid client_id or client_secret\",\"error\":\"invalid_client\",\"status\":400,\"cause\":[]}";

    private HttpServer server;
    private String base;
    private Logger exchangeLogger;
    private ListAppender<ILoggingEvent> logs;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<Scripted> scripted = new ArrayDeque<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.setExecutor(null);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        exchangeLogger = context.getLogger(MercadoTokenExchange.class.getName());
        logs = new ListAppender<>();
        logs.setContext(context);
        logs.start();
        exchangeLogger.addAppender(logs);
    }

    @AfterEach
    void stopServer() {
        exchangeLogger.detachAppender(logs);
        logs.stop();
        server.stop(0);
    }

    @Test
    void shouldUseVerifiedTokenEndpointLiteralAndRegisterUnderMercadoProtocol() {
        assertEquals("https://api.mercadolibre.com/oauth/token", MercadoTokenExchange.DEFAULT_TOKEN_URI,
                "§7：令牌端点字面值");
        MercadoTokenExchange exchange = exchange();

        assertEquals(PlatformAuthType.MERCADO_LIBRE.name(), exchange.type());
        assertTrue(exchange.rotatesRefreshToken(), "§2.2/§7：REFRESH_TOKEN 只能使用一次，轮换值必须回写落库");
    }

    @Test
    void shouldPostJsonBodyWithFourKeysAndNoBasicHeader() {
        scripted.add(new Scripted(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"bearer\","
                + "\"expires_in\":1234,\"refresh_token\":\"" + ROTATED_REFRESH_TOKEN + "\","
                + "\"scope\":\"offline_access read api\"}"));

        PlatformToken token = exchange().exchange(credential(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN));

        Recorded request = recorded.get(0);
        assertEquals("POST", request.method);
        assertEquals("/oauth/token", request.path);
        assertTrue(String.valueOf(request.header("content-type")).startsWith("application/json"),
                "§7 的凭据是放在 JSON 请求体里校验的，不是表单: " + request.header("content-type"));
        assertFalse(request.headers.containsKey("authorization"),
                "client_id/client_secret 在 body 里，不该有 Basic 头（那是 §4.1 eBay 的形状）");

        Map<String, Object> body = PlatformJson.object(request.body);
        assertEquals(4, body.size(), "只发 grant_type/refresh_token/client_id/client_secret 四个键: " + body.keySet());
        assertEquals("refresh_token", body.get("grant_type"));
        assertEquals(REFRESH_TOKEN, body.get("refresh_token"));
        assertEquals(CLIENT_ID, body.get("client_id"));
        assertEquals(CLIENT_SECRET, body.get("client_secret"));

        assertEquals(ACCESS_TOKEN, token.accessToken());
        assertEquals(ROTATED_REFRESH_TOKEN, token.refreshToken(), "一次性刷新令牌换出的新值必须交回上层落库");
    }

    @Test
    void shouldTakeExpiryFromExpiresInInsteadOfTheSixHourExample() {
        scripted.add(new Scripted(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":1234}"));

        PlatformToken token = exchange().exchange(credential(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN));

        // §7 的示例是 expires_in:10800，但正文明说「以响应 expires_in 为准」：用 1234 这个与示例值明显不同的秒数来证明没写死
        long seconds = Duration.between(LocalDateTime.now(), token.expiresAt()).toSeconds();
        assertTrue(seconds >= 1224 && seconds <= 1244, "到期时间应由响应的 expires_in 推出来，实测相差 " + seconds + " 秒");
        assertFalse(seconds >= 10700 && seconds <= 10900, "绝不能把 6 小时的示例值当成固定 TTL");
    }

    @Test
    void shouldKeepTokenValuesOutOfLogsAndToString() {
        scripted.add(new Scripted(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":10800,"
                + "\"refresh_token\":\"" + ROTATED_REFRESH_TOKEN + "\"}"));

        PlatformToken token = exchange().exchange(credential(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN));

        List<String> emitted = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertFalse(emitted.isEmpty(), "交换成功要留一条可排障的日志");
        for (String line : emitted) {
            assertFalse(line.contains(ACCESS_TOKEN), "日志不得出现访问令牌: " + line);
            assertFalse(line.contains(REFRESH_TOKEN), "日志不得出现旧刷新令牌: " + line);
            assertFalse(line.contains(ROTATED_REFRESH_TOKEN), "日志不得出现轮换出的新刷新令牌: " + line);
            assertFalse(line.contains(CLIENT_SECRET), "日志不得出现 client_secret: " + line);
        }
        assertFalse(token.toString().contains(ACCESS_TOKEN), "PlatformToken.toString 必须掩码: " + token);
        assertFalse(token.toString().contains(ROTATED_REFRESH_TOKEN), token.toString());
    }

    @Test
    void shouldNameTheMissingCredentialFieldWithoutLeakingAnyValue() {
        MercadoTokenExchange exchange = exchange();

        BusinessException noClientId = assertThrows(BusinessException.class,
                () -> exchange.exchange(credential("  ", CLIENT_SECRET, REFRESH_TOKEN)));
        BusinessException noSecret = assertThrows(BusinessException.class,
                () -> exchange.exchange(credential(CLIENT_ID, null, REFRESH_TOKEN)));
        BusinessException noRefresh = assertThrows(BusinessException.class,
                () -> exchange.exchange(credential(CLIENT_ID, CLIENT_SECRET, "  ")));
        BusinessException nullCredential = assertThrows(BusinessException.class, () -> exchange.exchange(null));

        for (BusinessException error : List.of(noClientId, noSecret, noRefresh, nullCredential)) {
            assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
            assertFalse(error.getMessage().contains(CLIENT_SECRET), "异常消息不得回显凭据值: " + error.getMessage());
            assertFalse(error.getMessage().contains(REFRESH_TOKEN), error.getMessage());
        }
        assertTrue(noClientId.getMessage().contains("apiKey"), noClientId.getMessage());
        assertTrue(noSecret.getMessage().contains("apiSecret"), noSecret.getMessage());
        assertTrue(noRefresh.getMessage().contains("refreshToken"), noRefresh.getMessage());
        assertTrue(recorded.isEmpty(), "凭据不全就不该发出令牌请求");
    }

    @Test
    void shouldSurfaceMeasuredErrorEnvelopeWithoutEchoingSecrets() {
        scripted.add(new Scripted(400, INVALID_CLIENT_FIXTURE));

        BusinessException error = assertThrows(BusinessException.class,
                () -> exchange().exchange(credential(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN)));

        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertTrue(error.getMessage().contains("invalid_client"), "要保留平台给的可排障错误码: " + error.getMessage());
        assertFalse(error.getMessage().contains(CLIENT_SECRET), error.getMessage());
        assertFalse(error.getMessage().contains(REFRESH_TOKEN), error.getMessage());
    }

    // ---------- 测试装置 ----------

    private MercadoTokenExchange exchange() {
        return new MercadoTokenExchange(new PlatformOAuthClient(httpExchange()), base + "/oauth/token");
    }

    private static PlatformHttpExchange httpExchange() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        // max-wait 压到 10ms：契约测试要跑退避分支，但不能真的睡很久
        return new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10));
    }

    private static PlatformCredential credential(String clientId, String clientSecret, String refreshToken) {
        return new PlatformCredential(PlatformAuthType.MERCADO_LIBRE, "tenant-1", "MERCADO", null,
                clientId, clientSecret, null, null, null, refreshToken, null, SELLER_USER_ID, null);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recorded.add(entry);

        Scripted next = scripted.isEmpty()
                ? new Scripted(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":10800}")
                : scripted.poll();
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
