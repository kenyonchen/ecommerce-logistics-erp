package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shopify Admin API 传输层契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，断言的是「我们怎么调用 Shopify」这一层协议事实：
 * 鉴权头、游标翻页、单页上限、限流退避、鉴权失败快速返回。响应体形状取自官方文档示例，
 * <b>但没有经过真实平台验证</b>（当前无沙箱凭据），上线前仍需凭据演练。
 *
 * @author kenyon
 */
class ShopifyAdminApiContractTest {

    private HttpServer server;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<Scripted> scripted = new ArrayDeque<>();

    private String storeUrl;
    private PlatformCredential credential;
    private ShopifyOrderFetcher fetcher;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.setExecutor(null);
        server.start();
        storeUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        credential = credential(storeUrl, "2026-01");
        fetcher = new ShopifyOrderFetcher(api(credential, 20, 3, Duration.ofMillis(20)));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldAuthenticateWithAccessTokenHeaderNotLegacyKeyHeaders() {
        scripted.add(new Scripted(200, ordersBody(1), null));

        fetcher.fetch(orderRequest(5), credential);

        Recorded request = recorded.get(0);
        assertEquals("shpat_test", request.header("X-Shopify-Access-Token"));
        assertFalse(request.headers.containsKey("X-API-Key"), "不得沿用自建店铺的 X-API-Key 约定");
        assertFalse(request.headers.containsKey("X-API-Secret"));
        assertTrue(request.path.startsWith("/admin/api/2026-01/orders.json"), request.path);
    }

    @Test
    void shouldClampPageSizeToTwoHundredAndFiftyAndSendUtcIncrementalWindow() {
        scripted.add(new Scripted(200, ordersBody(1), null));

        fetcher.fetch(new PlatformOrderFetchRequest("tenant-1", "SHOPIFY", storeUrl, "ak", "",
                null, LocalDateTime.parse("2026-01-25T00:00:00"), LocalDateTime.parse("2026-03-22T00:00:00"),
                500, 1), credential);

        String query = recorded.get(0).query;
        assertTrue(query.contains("limit=250"), "Shopify 单页上限 250，超出的必须压回来: " + query);
        assertTrue(query.contains("updated_at_min=2026-01-25T00%3A00%3A00Z")
                || query.contains("updated_at_min=2026-01-25T00:00:00Z"), "增量窗口要用 ISO8601 UTC: " + query);
        assertTrue(query.contains("updated_at_max="), query);
    }

    @Test
    void shouldFollowLinkHeaderCursorAndNeverSendPageParameter() {
        String nextCursor = storeUrl + "/admin/api/2026-01/orders.json?page_info=CURSOR2&limit=250";
        scripted.add(new Scripted(200, ordersBody(1), "<" + nextCursor + ">; rel=\"next\""));
        scripted.add(new Scripted(200, ordersBody(2), null));

        List<Map<String, Object>> rows = fetcher.fetch(orderRequest(10), credential);

        assertEquals(2, recorded.size());
        assertEquals(3, rows.size(), "两页共 1 + 2 条");
        // 续页必须原样使用 Shopify 给的 URL（含 page_info），不能自己拼页码
        assertTrue(recorded.get(1).query.contains("page_info=CURSOR2"), recorded.get(1).query);
        assertTrue(recorded.stream().noneMatch(r -> r.query.contains("?page=") || r.query.contains("&page=")),
                "REST 带 page 参数会被 Shopify 拒绝，绝不能发出去");
    }

    @Test
    void shouldStopPagingAtConfiguredMaxPages() {
        String nextCursor = storeUrl + "/admin/api/2026-01/orders.json?page_info=LOOP&limit=250";
        ShopifyOrderFetcher bounded = new ShopifyOrderFetcher(
                api(credential(storeUrl, "2026-01"), 3, 1, Duration.ofMillis(10)));
        for (int i = 0; i < 10; i++) {
            scripted.add(new Scripted(200, ordersBody(1), "<" + nextCursor + ">; rel=\"next\""));
        }

        bounded.fetch(orderRequest(1000), credential);

        assertEquals(3, recorded.size(), "页数上限是防失控的保险丝，到点必须停");
    }

    @Test
    void shouldRetryOnRateLimitAndThenSucceed() {
        scripted.add(new Scripted(429, "{\"errors\":\"Throttled\"}", null));
        scripted.add(new Scripted(200, ordersBody(1), null));

        List<Map<String, Object>> rows = fetcher.fetch(orderRequest(5), credential);

        assertEquals(2, recorded.size(), "429 属于暂时性失败，应退避重试一次");
        assertEquals(1, rows.size());
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutWastingQuota() {
        scripted.add(new Scripted(401, "{\"errors\":\"[API] Invalid API key\"}", null));

        BusinessException error = assertThrows(BusinessException.class, () -> fetcher.fetch(orderRequest(5), credential));

        assertEquals(1, recorded.size(), "令牌错误重试没有意义，只会白耗平台配额");
        assertEquals("SHOPIFY_REQUEST_FAILED", error.getErrorCode());
        assertTrue(error.getMessage().contains("accessToken"), error.getMessage());
    }

    @Test
    void shouldGiveUpAfterRepeatedServerErrors() {
        for (int i = 0; i < 5; i++) {
            scripted.add(new Scripted(503, "unavailable", null));
        }

        BusinessException error = assertThrows(BusinessException.class, () -> fetcher.fetch(orderRequest(5), credential));

        assertEquals(3, recorded.size(), "按 retry.max-attempts 重试完就放弃，不能无限重试");
        assertTrue(error.getMessage().contains("3 次"), error.getMessage());
    }

    /**
     * ISS-131：平台已经把状态码和错误体给出来了，就别再把同一个状态用异常类名复述一遍，
     * 也不要出现包装层那种 {@code ": null"} 尾巴。
     */
    @Test
    void shouldNotRestateStatusWhenPlatformAlreadyGaveOne() {
        scripted.add(new Scripted(404, "{\"errors\":\"There is no such thing as a shop named br18probe\"}", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher.fetch(orderRequest(5), credential));

        assertTrue(error.getMessage().contains("404"), error.getMessage());
        assertFalse(error.getMessage().contains("NotFound:"), "异常类名重复复述同一个 404: " + error.getMessage());
        assertFalse(error.getMessage().endsWith(": null"), error.getMessage());
    }

    /**
     * ISS-131：连接层失败的真凶在 cause 链深处（{@code ResourceAccessException} 自己的消息以
     * {@code ": null"} 结尾），必须按族说清是「连不上」而不是「限流」，并附根因。
     */
    @Test
    void shouldReportRefusedConnectionAsUnreachableWithRootCause() {
        ShopifyOrderFetcher unreachable = new ShopifyOrderFetcher(
                api(credential("http://127.0.0.1:1", "2026-01"), 20, 2, Duration.ofMillis(10)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> unreachable.fetch(orderRequest(5), credential("http://127.0.0.1:1", "2026-01")));

        assertTrue(error.getMessage().contains("无法连接"), error.getMessage());
        assertFalse(error.getMessage().contains("限流或平台不可用"), error.getMessage());
        assertTrue(error.getMessage().contains("ConnectException"), "应附连接层根因: " + error.getMessage());
        assertFalse(error.getMessage().endsWith(": null"), error.getMessage());
        assertEquals("SHOPIFY_REQUEST_FAILED", error.getErrorCode());
    }

    @Test
    void shouldRequireApiVersionInsteadOfHardcodingOne() {
        ShopifyOrderFetcher versionless = new ShopifyOrderFetcher(
                api(credential(storeUrl, ""), 20, 1, Duration.ofMillis(10)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> versionless.fetch(orderRequest(5), credential(storeUrl, "")));

        assertEquals("SHOPIFY_API_VERSION_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty(), "版本没配就不该发出任何请求");
    }

    @Test
    void shouldRejectAdminConsoleDomainAsStoreUrl() {
        ShopifyOrderFetcher wrongHost = new ShopifyOrderFetcher(
                api(credential("https://admin.shopify.com/store/demo", "2026-01"), 20, 1, Duration.ofMillis(10)));
        PlatformCredential badCredential = credential("https://admin.shopify.com/store/demo", "2026-01");

        BusinessException error = assertThrows(BusinessException.class,
                () -> wrongHost.fetch(orderRequest(5), badCredential));

        assertEquals("SHOPIFY_STORE_URL_INVALID", error.getErrorCode());
    }

    @Test
    void shouldRequireAccessToken() {
        PlatformCredential tokenless = new PlatformCredential(PlatformAuthType.SHOPIFY, "tenant-1", "SHOPIFY",
                storeUrl, "ak", "", null, null, "2026-01", null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new ShopifyOrderFetcher(api(tokenless, 20, 1, Duration.ofMillis(10)))
                        .fetch(orderRequest(5), tokenless));

        assertEquals("PLATFORM_ACCESS_TOKEN_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().toString();
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, "{\"orders\":[]}", null) : scripted.poll();
        byte[] body = next.body.getBytes(StandardCharsets.UTF_8);
        if (next.linkHeader != null) {
            exchange.getResponseHeaders().add("Link", next.linkHeader);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(next.status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private ShopifyAdminApi api(PlatformCredential credential, int maxPages, int attempts, Duration maxWait) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new ShopifyAdminApi(RestClient.builder(), factory, "", maxPages, attempts, maxWait);
    }

    private static PlatformCredential credential(String storeUrl, String apiVersion) {
        return new PlatformCredential(PlatformAuthType.SHOPIFY, "tenant-1", "SHOPIFY", storeUrl,
                "ak", "", "shpat_test", null, apiVersion, null, null, null, null);
    }

    private static PlatformOrderFetchRequest orderRequest(int limit) {
        return new PlatformOrderFetchRequest("tenant-1", "SHOPIFY", null, null, null,
                null, null, null, limit, 1);
    }

    /**
     * 生成含 {@code count} 条订单的响应体；订单号取自全局序列，保证跨页/跨用例都不重复。
     */
    private static String ordersBody(int count) {
        StringBuilder orders = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (orders.length() > 0) {
                orders.append(',');
            }
            orders.append("{\"id\":").append(ORDER_ID_SEQUENCE.incrementAndGet())
                    .append(",\"email\":\"buyer").append(i).append("@example.com\"}");
        }
        return "{\"orders\":[" + orders + "]}";
    }

    private static final java.util.concurrent.atomic.AtomicLong ORDER_ID_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong(1000);

    private static final class Recorded {
        private String method;
        private String path;
        private String query;
        private final Map<String, String> headers = new java.util.LinkedHashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Scripted(int status, String body, String linkHeader) {
    }
}
