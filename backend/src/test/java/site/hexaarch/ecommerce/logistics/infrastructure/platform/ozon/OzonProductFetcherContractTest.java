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
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportTargetField;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
 * Ozon 商品适配器的两步链式契约测试。
 * <p>
 * §8（docs/spec/platform-api-reference.md）核实的是「<b>没有商品列表接口</b>」这件事，
 * 于是本适配器最重要的三条形状都在这里断言：
 * <ol>
 *   <li>第一步 {@code POST /v3/product/list}（{@code filter{visibility} + last_id + limit}）只出 id 清单，
 *       第二步 {@code POST /v4/product/info/attributes} 才出属性，两者必须按这个顺序各打一次；</li>
 *   <li>翻页靠请求侧的 {@code last_id} 链式推进（第一页返回什么，第二页就带什么）；</li>
 *   <li>§8 点名的坑：<b>属性行返回的是 {@code id} 而不是 {@code product_id}</b>，
 *       所以行里的平台商品 ID 必须取 {@code id}。</li>
 * </ol>
 * 夹取也在这里断言：{@code limit} ≤1000（§8 的订单/属性上限），属性接口未指定时按 §8 的默认 100。
 * <p>
 * 属性对象的键名未经真实平台验证（§8 只核实到端点、{@code filter} 形状与「返回 id」这条；ISS-065），
 * fixture 仅验证候选探测与 {@code ImportTargetField} 别名对齐，不能作为平台侧结构的证据。
 *
 * @author kenyon
 */
class OzonProductFetcherContractTest {

    private static final String CLIENT_ID = "215000371";
    private static final String API_KEY = "secret-api-key-never-echoed";

    private static final String ID_LIST_PAGE1_FIXTURE = """
            {"result":{"items":[{"product_id":4711},{"product_id":4712}],"last_id":"4712","total":2}}""";

    private static final String ID_LIST_EMPTY_FIXTURE = """
            {"result":{"items":[],"last_id":"","total":2}}""";

    /**
     * 属性页：§8 说这些行给的是 {@code id}（没有 {@code product_id}）；第二行只有 {@code visibility}，
     * 用来验证停售商品被判成 {@code active=false} 而不是漏掉
     */
    private static final String ATTRIBUTES_FIXTURE = """
            {"result":[
              {"id":"4711","offer_id":"TEE-S-BLK","name":"Classic Tee","description":"100% cotton",
               "status":"visible","weight":"0.2","weight_unit":"kg",
               "attributes":[{"name":"Color","values":[{"text":"Black"},{"text":"Small"}]}]},
              {"id":"4712","offer_id":"HOOD-1","name":"Hoodie","visibility":"hidden"}
            ]}""";

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

    // ---------- 两步链式 ----------

    @Test
    void shouldChainListIdsIntoAttributesRequestAndMapReturnedId() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 10), credential());

        assertEquals(List.of(OzonSellerApi.PRODUCT_LIST_PATH, OzonSellerApi.PRODUCT_ATTRIBUTES_PATH,
                        OzonSellerApi.PRODUCT_LIST_PATH), recorded.stream().map(entry -> entry.path).toList(),
                "§8：先 /v3/product/list 取 id，再 /v4/product/info/attributes 取属性");
        assertEquals(2, rows.size());
        // §8 的坑：属性行给 id 而不是 product_id
        assertEquals("4711", rows.get(0).get("external_id"));
        assertEquals("4712", rows.get(1).get("external_id"));
        assertFalse(rows.get(0).containsKey("product_id"), "不能把平台原始键 product_id 抛给导入侧");

        Map<String, Object> attributesBody = PlatformJson.object(recorded.get(1).body);
        assertEquals(List.of("4711", "4712"), PlatformJson.path(attributesBody, "filter", "product_id"),
                "第一步的 id 清单要原样喂给第二步的 filter.product_id");
        assertEquals("", attributesBody.get("last_id"), "首次属性请求的游标是空串而不是省略键");
        for (Recorded request : recorded) {
            assertEquals(CLIENT_ID, request.header(OzonSellerApi.CLIENT_ID_HEADER));
            assertEquals(API_KEY, request.header(OzonSellerApi.API_KEY_HEADER));
            assertFalse(request.headers.containsKey("authorization"));
            assertFalse(request.headers.containsKey("x-api-key"));
        }
    }

    @Test
    void shouldCarryLastIdFromThePreviousPageIntoTheNextListRequest() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE.replace("4712", "5001")));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE.replace("4711", "5001").replace("4712", "5002")));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        fetcher().fetch(productRequest(null, 10), credential());

        assertEquals(5, recorded.size(), "第一页有 last_id 就要跟着翻第二页");
        assertEquals("", PlatformJson.object(recorded.get(0).body).get("last_id"));
        assertEquals("4712", PlatformJson.object(recorded.get(2).body).get("last_id"),
                "§8：/v3/product/list 用 last_id 翻页，游标必须由上一页响应链过来");
        assertEquals("ALL", PlatformJson.path(PlatformJson.object(recorded.get(2).body), "filter", "visibility"),
                "§8 核实了 filter 里有 visibility 这个键");
    }

    @Test
    void shouldStopPagingWhenThePlatformRepeatsTheSameLastId() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 10), credential());

        assertEquals(4, recorded.size(), "游标不推进就收手，不能翻第三页重复拉同一批 id");
        assertEquals(2, rows.size(), "重复页里的同一商品不能重复出行");
    }

    // ---------- 夹取 ----------

    @Test
    void shouldClampBothStepsToOneThousand() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        fetcher().fetch(productRequest(null, 5000), credential());

        assertEquals(1000, PlatformJson.object(recorded.get(0).body).get("limit"),
                "第一步的上限 §8 没给，沿用同一保守上限 1000");
        assertEquals(1000, PlatformJson.object(recorded.get(1).body).get("limit"), "§8：属性接口 limit 上限 1000");
    }

    @Test
    void shouldUseTheVerifiedAttributesDefaultOfOneHundredWhenNoLimitIsGiven() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        fetcher().fetch(new PlatformProductFetchRequest("tenant-1", "OZON", null, null, null, null, null, 1),
                credential());

        Map<String, Object> listBody = PlatformJson.object(recorded.get(0).body);
        assertEquals(50, listBody.get("limit"), "没有指定条数时按适配器的总条数默认，而不是一次换回 1000 个 id");
        Map<String, Object> attributesBody = PlatformJson.object(recorded.get(1).body);
        assertEquals(100, attributesBody.get("limit"), "§8：属性接口默认 100");
    }

    // ---------- 行映射 ----------

    @Test
    void shouldBridgeAttributeFieldsIntoImportAliasesAndConvertWeightToGrams() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        Map<String, Object> row = fetcher().fetch(productRequest(null, 10), credential()).get(0);

        assertEquals("4711", row.get("external_id"));
        assertEquals("Classic Tee", row.get("title"));
        assertEquals("TEE-S-BLK", row.get("sku"));
        assertEquals("100% cotton", row.get("description"));
        assertEquals("200", row.get("weight"), "0.2 kg 必须换算成克，目标字段是 WEIGHT_IN_GRAMS");
        assertEquals("Color:Black/Small", row.get("attributes"));
        assertEquals(true, row.get("active"));
        assertFalse(row.containsKey("price"), "属性接口没有已核实的售价字段，探不到就不透出该键");
        for (String key : row.keySet()) {
            assertTrue(ImportTargetField.detectByAlias(key).isPresent(),
                    "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
        }
        assertFalse(row.containsKey("offer_id"), "不得把平台原始键直接抛给导入侧");
    }

    @Test
    void shouldMarkHiddenProductInactiveAndLeaveUnverifiedKeysOut() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        Map<String, Object> row = fetcher().fetch(productRequest(null, 10), credential()).get(1);

        assertEquals("4712", row.get("external_id"));
        assertEquals("HOOD-1", row.get("sku"));
        assertEquals(false, row.get("active"), "hidden 不能被当成在售商品");
        assertFalse(row.containsKey("weight"));
        assertFalse(row.containsKey("attributes"));
        assertFalse(row.containsKey("description"));
    }

    @Test
    void shouldFilterRowsByKeywordAfterTheTwoStepBecauseTheIdEndpointHasNoSearchParameter() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));
        scripted.add(new Scripted(200, ID_LIST_EMPTY_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest("hood", 10), credential());

        assertEquals(1, rows.size());
        assertEquals("4712", rows.get(0).get("external_id"));
    }

    @Test
    void shouldRespectRequestedTotalLimitAcrossBothSteps() {
        scripted.add(new Scripted(200, ID_LIST_PAGE1_FIXTURE));
        scripted.add(new Scripted(200, ATTRIBUTES_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 1), credential());

        assertEquals(1, rows.size());
        assertEquals(2, recorded.size(), "取满总条数就不再打第二页的 id 清单");
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(productRequest(null, 5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void shouldFailFastOnUnauthorizedWithoutLeakingTheApiKey() {
        scripted.add(new Scripted(401, "{\"code\":0,\"message\":\"api key is invalid\"}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(productRequest(null, 5), credential()));

        assertEquals(1, recorded.size());
        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains(API_KEY), "异常消息不得回显 Api-Key: " + error.getMessage());
    }

    @Test
    void shouldClassifyVisibilityAndWeightWithoutInventingUnverifiedValues() {
        assertNull(OzonProductFetcher.bridgeActive(null));
        assertNull(OzonProductFetcher.bridgeActive("limited"), "认不出的可见性不透出布尔，而不是硬塞在售");
        assertEquals(false, OzonProductFetcher.bridgeActive("INACTIVE"));
        assertEquals(false, OzonProductFetcher.bridgeActive("archived"), "inactive 含 active，停用必须优先判");
        assertEquals(true, OzonProductFetcher.bridgeActive("VISIBLE"));
        assertEquals("200", OzonProductFetcher.toGrams(Map.of("weight", "0.2", "weight_unit", "kg")));
        assertNull(OzonProductFetcher.toGrams(Map.of("name", "no weight")));
    }

    // ---------- 测试装置 ----------

    private OzonProductFetcher fetcher() {
        return new OzonProductFetcher(api());
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

    private static PlatformProductFetchRequest productRequest(String keyword, Integer limit) {
        return new PlatformProductFetchRequest("tenant-1", "OZON", null, null, null, keyword, limit, 1);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, ID_LIST_EMPTY_FIXTURE) : scripted.poll();
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
