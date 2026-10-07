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
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentResult;
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
 * Ozon 发货回写的请求形状契约测试。
 * <p>
 * §8 把这一家列为「按记忆写就会写错」的位置，本测试因此逐字断言两处：端点是
 * <b>{@code POST /v2/posts/add}（不是 {@code /v4/…} 那一族）</b>，字段是
 * <b>{@code tracking_number}（不是拼写极像的 {@code tracing_number}）</b>。
 * 其余按 §8 的体形状断言 {@code {status:"awaiting_delivery", tracking_number,
 * posts:[{posting_number, tracking_status}]}}，并且不发任何未核实的键
 * （承运商、跟踪链接、通知买家在 §8 的 Ozon 体里都没有对应字段）。
 * <p>
 * 响应体结构未被 §8 核实，fixture 只验证候选探测与「探不到就回本次发出去的贴子号」的兜底。
 *
 * @author kenyon
 */
class OzonFulfillmentPusherContractTest {

    private static final String CLIENT_ID = "215000371";
    private static final String API_KEY = "secret-api-key-never-echoed";
    private static final String POSTING = "0998957729-0001";

    private static final Pattern OFFSET_TIMESTAMP =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?([+-]\\d{2}:\\d{2}|Z)$");

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
    void shouldPostVerifiedV2EndpointWithTrackingNumberNotTracingNumber() {
        scripted.add(new Scripted(200, """
                {"result":[{"posting_number":"0998957729-0001","status":"success",
                            "in_process_at":"2026-03-22T12:00:00+03:00"}]}"""));

        PlatformFulfillmentResult result = pusher().push(
                new PlatformFulfillmentRequest(POSTING, "RA001234567RU", "Russian Post", "https://track.example/1", true),
                credential());

        assertEquals(1, recorded.size());
        Recorded request = recorded.get(0);
        assertEquals("POST", request.method);
        assertEquals("/v2/posts/add", request.path, "§8：现役端点是 /v2/posts/add，不是 /v4/... 那一族");
        assertEquals(OzonSellerApi.FULFILLMENT_PATH, request.path);
        assertEquals(CLIENT_ID, request.header(OzonSellerApi.CLIENT_ID_HEADER));
        assertEquals(API_KEY, request.header(OzonSellerApi.API_KEY_HEADER));
        assertFalse(request.headers.containsKey("authorization"), "§8：Ozon 没有 Bearer/OAuth");
        assertFalse(request.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头");

        Map<String, Object> body = PlatformJson.object(request.body);
        assertEquals("awaiting_delivery", body.get("status"));
        assertEquals("RA001234567RU", body.get("tracking_number"), "§8：字段名是 tracking_number");
        assertFalse(body.containsKey("tracing_number"), "§8 点名的坑：不是 tracing_number，写错一个字母平台就拒单");
        List<Map<String, Object>> posts = PlatformJson.objects(body.get("posts"));
        assertEquals(1, posts.size());
        assertEquals(POSTING, posts.get(0).get("posting_number"));
        assertEquals("awaiting_delivery", posts.get(0).get("tracking_status"));
        assertFalse(body.containsKey("carrier") || body.containsKey("shipping_carrier_code"),
                "§8 的 Ozon 体里没有承运商字段，不臆造");
        assertFalse(body.containsKey("tracking_url") || body.containsKey("notify"),
                "§8 的 Ozon 体里没有跟踪链接/通知买家字段，不臆造");
        // 本请求体不含时间字段，但一旦有人加了 *At，就必须带偏移（§8 的坑）
        assertTrue(timestampsIn(body).stream().allMatch(value -> OFFSET_TIMESTAMP.matcher(value).matches()),
                " outbound 时间字段必须带偏移: " + timestampsIn(body));

        assertEquals(POSTING, result.platformFulfillmentId());
        assertEquals("success", result.status());
        assertEquals(1, result.lineItemCount());
        assertEquals(LocalDateTime.parse("2026-03-22T09:00:00"), result.fulfilledAt(),
                "+03:00 要归一成 UTC 墙上时间");
    }

    @Test
    void shouldFallBackToThePostedNumberWhenThePlatformEchoesNothing() {
        scripted.add(new Scripted(200, "{\"result\":[]}"));

        PlatformFulfillmentResult result = pusher().push(
                new PlatformFulfillmentRequest(POSTING, "RA001234567RU", null, null, false), credential());

        assertEquals(POSTING, result.platformFulfillmentId());
        assertNull(result.status());
        assertNull(result.fulfilledAt());
        assertEquals(1, result.lineItemCount());
    }

    @Test
    void shouldRefuseMissingTrackingNumberBeforeAnyRequest() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(POSTING, "  ", "Russian Post", null, false),
                        credential()));

        assertEquals("OZON_TRACKING_NUMBER_REQUIRED", error.getErrorCode());
        assertTrue(error.getMessage().contains("tracking_number"), error.getMessage());
        assertTrue(recorded.isEmpty(), "缺运单号不该出网");
    }

    @Test
    void shouldRefuseUnsafePostingNumberBeforeAnyRequest() {
        BusinessException path = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest("0998/../etc", "RA1", null, null, false), credential()));
        BusinessException blank = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(null, "RA1", null, null, false), credential()));
        BusinessException emptyRequest = assertThrows(BusinessException.class,
                () -> pusher().push(null, credential()));

        assertEquals("OZON_POSTING_NUMBER_INVALID", path.getErrorCode());
        assertEquals("OZON_POSTING_NUMBER_INVALID", blank.getErrorCode());
        assertEquals("OZON_FULFILLMENT_REQUEST_INVALID", emptyRequest.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutLeakingTheApiKey() {
        scripted.add(new Scripted(401, "{\"code\":0,\"message\":\"api key is invalid\"}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(POSTING, "RA1", "Russian Post", null, false),
                        credential()));

        assertEquals(1, recorded.size(), "§8：401 是凭据问题，重试没有意义");
        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains(API_KEY), "异常消息不得回显 Api-Key: " + error.getMessage());
    }

    @Test
    void shouldRetryOnceOnTooManyRequests() {
        scripted.add(new Scripted(429, "{\"code\":0,\"message\":\"too many requests\"}"));
        scripted.add(new Scripted(200, "{\"result\":[{\"posting_number\":\"0998957729-0001\"}]}"));

        PlatformFulfillmentResult result = pusher().push(
                new PlatformFulfillmentRequest(POSTING, "RA1", "Russian Post", null, false), credential());

        assertEquals(2, recorded.size(), "§2.3：429 按退避重试");
        assertEquals(POSTING, result.platformFulfillmentId());
    }

    @Test
    void shouldRegisterUnderTheOzonProtocolKey() {
        assertEquals(PlatformAuthType.OZON.name(), pusher().type());
    }

    // ---------- 测试装置 ----------

    private OzonFulfillmentPusher pusher() {
        return new OzonFulfillmentPusher(api());
    }

    private OzonSellerApi api() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new OzonSellerApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)), 20);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.OZON, "tenant-1", "OZON", storeUrl,
                CLIENT_ID, API_KEY, null, null, null, null, null, null, null);
    }

    /**
     * 递归收集请求体里所有像时间戳的字符串值，用于「一旦有 {@code *At} 就必须带偏移」的断言。
     */
    private static List<String> timestampsIn(Object node) {
        List<String> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(Object node, List<String> found) {
        if (node instanceof Map<?, ?> map) {
            map.values().forEach(value -> collect(value, found));
            return;
        }
        if (node instanceof List<?> list) {
            list.forEach(value -> collect(value, found));
            return;
        }
        if (node instanceof String text && text.matches("^\\d{4}-\\d{2}-\\d{2}T.*")) {
            found.add(text);
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, "{\"result\":[]}") : scripted.poll();
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
