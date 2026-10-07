package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

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
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformOAuthClient;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformToken;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * eBay Sell 发货回写与 OAuth 令牌交换的契约测试。
 * <p>
 * 用本地 {@link HttpServer} 顶替平台，验证两件事：
 * <ol>
 *   <li>pusher 先用 {@code orderIds} 精确取回订单、从 {@code orderLineItems} 解析 required 的 {@code lineItems}，
 *       再 {@code POST .../shipping_fulfillment} 带成对的 {@code trackingNumber}+{@code shippingCarrierCode} 与行项目，
 *       且不发 readOnly 的 {@code status}、不发未核实的「通知买家」；</li>
 *   <li>令牌交换是 {@code POST .../identity/v1/oauth2/token} 的 Basic 头 + 表单，scope 两把空格分隔，且<b>不轮换</b>。</li>
 * </ol>
 * 响应体字段名未经真实平台验证（§4.2/§4.3，ISS-065），fixture 仅验证形状对齐。
 *
 * @author kenyon
 */
class EbayFulfillmentPusherContractTest {

    private static final String BEARER = "v__ebay_oauth_token_test";
    private static final String APP_ID = "ebay_appid_test";
    private static final String APP_SECRET = "ebay_secret_test";
    private static final String REFRESH_TOKEN = "rt_test_refresh";
    private static final String ORDER_ID = "01-11111-11111";

    /**
     * 精确取单响应：两行 orderLineItems，逐行带 lineItem.id 与 quantityPurchased（§4.2 结构未核实，探测候选键）
     */
    private static final String ORDER_LOOKUP_FIXTURE = """
            {"order":[{"orderId":"01-11111-11111","orderLineItems":[
              {"lineItem":{"id":"01-11111-11111-1"},"quantityPurchased":2},
              {"lineItem":{"id":"01-11111-11111-2"},"quantityPurchased":1}]}]}""";

    private static final String CREATED_FIXTURE = """
            {"fulfillmentId":"9000012345","status":"IN_TRANSIT","createdTime":"2026-03-22T12:00:00.000Z"}""";

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

    // ---------- 发货回写 ----------

    @Test
    void shouldFetchLineItemsThenPostCarrierAndTrackingAsAPair() {
        scripted.add(new Scripted(200, ORDER_LOOKUP_FIXTURE));
        scripted.add(new Scripted(200, CREATED_FIXTURE));

        PlatformFulfillmentResult result = pusher().push(
                new PlatformFulfillmentRequest(ORDER_ID, "1Z999AA1", "UPS", null, true), credential());

        assertEquals(2, recorded.size(), "两步：先 GET 取行项目再 POST 发货");
        Recorded lookup = recorded.get(0);
        assertEquals("GET", lookup.method);
        assertEquals("/sell/fulfillment/v1/order", lookup.path);
        assertTrue(decode(lookup.query).contains("orderIds=" + ORDER_ID),
                "§4.2：用 orderIds 精确取单解析 required lineItems: " + lookup.query);

        Recorded post = recorded.get(1);
        assertEquals("POST", post.method);
        assertEquals("/sell/fulfillment/v1/order/01-11111-11111/shipping_fulfillment", post.path);
        assertEquals("Bearer " + BEARER, post.header("authorization"));
        assertFalse(post.headers.containsKey("x-api-key"), "§4.1：eBay 用 Bearer 不是 X-API-Key");

        Map<String, Object> body = PlatformJson.object(post.body);
        assertEquals("UPS", body.get("shippingCarrierCode"));
        assertEquals("1Z999AA1", body.get("trackingNumber"));
        List<Map<String, Object>> lineItems = PlatformJson.objects(body.get("lineItems"));
        assertEquals(2, lineItems.size());
        assertEquals("01-11111-11111-1", lineItems.get(0).get("id"));
        assertEquals(2, lineItems.get(0).get("quantity"));
        assertFalse(body.containsKey("status"), "§4.2：status 是 readOnly，卖家不能指定，不能发");
        assertFalse(body.containsKey("notifyCustomer") || body.containsKey("notify"),
                "§4 没有核实到的通知买家开关，绝不臆造字段");

        assertEquals("9000012345", result.platformFulfillmentId());
        assertEquals("IN_TRANSIT", result.status());
        assertEquals(2, result.lineItemCount());
        assertEquals(LocalDateTime.parse("2026-03-22T12:00:00"), result.fulfilledAt());
    }

    @Test
    void shouldRejectIncompleteTrackingPairBeforeAnyRequest() {
        BusinessException noCarrier = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(ORDER_ID, "1Z999AA1", "  ", null, false), credential()));
        BusinessException noNumber = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(ORDER_ID, null, "UPS", null, false), credential()));

        assertEquals("EBAY_TRACKING_PAIR_REQUIRED", noCarrier.getErrorCode());
        assertEquals("EBAY_TRACKING_PAIR_REQUIRED", noNumber.getErrorCode());
        assertTrue(recorded.isEmpty(), "成对校验先于外呼，缺运单要素不该发请求");
    }

    @Test
    void shouldRejectUnsafeOrderIdWithoutRequest() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest("1/../2", "1Z999AA1", "UPS", null, false), credential()));

        assertEquals("EBAY_ORDER_ID_INVALID", error.getErrorCode());
        assertTrue(recorded.isEmpty(), "非法订单号不能拼进请求路径");
    }

    @Test
    void shouldFailLoudlyWhenOrderCannotBeResolved() {
        scripted.add(new Scripted(200, "{\"order\":[]}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(ORDER_ID, "1Z999AA1", "UPS", null, false), credential()));

        assertEquals("EBAY_ORDER_NOT_FOUND", error.getErrorCode());
        assertEquals(1, recorded.size(), "取不到单就不该继续 POST 发货");
    }

    @Test
    void shouldFailLoudlyWhenOrderHasNoResolvableLineItems() {
        scripted.add(new Scripted(200, "{\"order\":[{\"orderId\":\"01-11111-11111\",\"orderLineItems\":[]}]}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> pusher().push(new PlatformFulfillmentRequest(ORDER_ID, "1Z999AA1", "UPS", null, false), credential()));

        assertEquals("EBAY_LINE_ITEMS_REQUIRED", error.getErrorCode());
        assertEquals(1, recorded.size());
    }

    // ---------- 令牌交换 ----------

    @Test
    void shouldUseVerifiedEbayTokenEndpointLiteral() {
        assertEquals("https://api.ebay.com/identity/v1/oauth2/token", EbayOAuthTokenExchange.DEFAULT_TOKEN_URI);
    }

    @Test
    void shouldExchangeWithBasicHeaderAndFormCarryingScope() {
        scripted.add(new Scripted(200, "{\"access_token\":\"" + BEARER
                + "\",\"token_type\":\"user\",\"expires_in\":7200}"));
        EbayOAuthTokenExchange exchange =
                new EbayOAuthTokenExchange(new PlatformOAuthClient(httpExchange()), storeUrl + "/identity/v1/oauth2/token");

        PlatformToken token = exchange.exchange(credential());

        assertEquals(BEARER, token.accessToken());
        assertNull(token.refreshToken(), "§2.2：eBay 不轮换刷新令牌，不应凭空造出刷新令牌");
        assertFalse(exchange.rotatesRefreshToken(), "§2.2 判定表：eBay rotatesRefreshToken=false");
        assertEquals(PlatformAuthType.EBAY_SELL.name(), exchange.type());

        Recorded request = recorded.get(0);
        assertEquals("POST", request.method);
        assertEquals("/identity/v1/oauth2/token", request.path);
        assertEquals(PlatformOAuthClient.basicAuthHeader(APP_ID, APP_SECRET), request.header("authorization"),
                "§4.1：客户端凭据在 Basic 头，不是表单体");
        assertTrue(String.valueOf(request.header("content-type")).startsWith("application/x-www-form-urlencoded"),
                "令牌端点要求 urlencoded: " + request.header("content-type"));
        Map<String, String> form = parseForm(request.body);
        assertEquals("refresh_token", form.get("grant_type"));
        assertEquals(REFRESH_TOKEN, form.get("refresh_token"));
        assertEquals("https://api.ebay.com/oauth/api_scope/sell.fulfillment"
                        + " https://api.ebay.com/oauth/api_scope/sell.inventory", form.get("scope"),
                "§4.1：scope 两把 fulfillment/inventory，空格分隔");
    }

    @Test
    void shouldRefuseToExchangeWhenCredentialMissingWithoutLeakingValues() {
        PlatformCredential noRefresh = new PlatformCredential(PlatformAuthType.EBAY_SELL, "tenant-1", "EBAY",
                null, APP_ID, APP_SECRET, null, null, null, "   ", null, null, "EBAY_US");
        PlatformCredential noSecret = new PlatformCredential(PlatformAuthType.EBAY_SELL, "tenant-1", "EBAY",
                null, APP_ID, null, null, null, null, REFRESH_TOKEN, null, null, "EBAY_US");
        EbayOAuthTokenExchange exchange =
                new EbayOAuthTokenExchange(new PlatformOAuthClient(httpExchange()), storeUrl + "/identity/v1/oauth2/token");

        BusinessException missingRefresh = assertThrows(BusinessException.class, () -> exchange.exchange(noRefresh));
        BusinessException missingSecret = assertThrows(BusinessException.class, () -> exchange.exchange(noSecret));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", missingRefresh.getErrorCode());
        assertTrue(missingRefresh.getMessage().contains("refreshToken"), missingRefresh.getMessage());
        assertTrue(missingSecret.getMessage().contains("apiSecret"), missingSecret.getMessage());
        assertFalse(missingSecret.getMessage().contains(APP_SECRET), "异常消息不得回显凭据值(BR-02)");
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private EbayFulfillmentPusher pusher() {
        return new EbayFulfillmentPusher(api());
    }

    private EbaySellApi api() {
        return new EbaySellApi(httpExchange(), 20);
    }

    private PlatformHttpExchange httpExchange() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10));
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.EBAY_SELL, "tenant-1", "EBAY", storeUrl,
                APP_ID, APP_SECRET, BEARER, null, null, REFRESH_TOKEN, null, null, "EBAY_US");
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
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
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, "{\"order\":[]}") : scripted.poll();
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
