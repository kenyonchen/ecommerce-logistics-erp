package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shopee 商品适配器（{@link ShopeeProductFetcher}）的请求形状、翻页与变体展开契约测试。
 * <p>
 * 断言的是「怎么调 {@code POST /api/v2/product/get_item_list} 并把商品变成导入侧行」：路径挂在
 * <b>{@code product/} 而非 {@code item/}</b>（§6 点名的坑）、必填 {@code offset}/{@code page_size}(≤100)/
 * {@code item_status[]} 进 body、{@code has_next_page}+{@code next_offset} 链式翻页、游标不推进时收手、
 * 有 {@code variations[]} 时一变体一行、无变体时商品出一行、商品数组键按候选探测（{@code item}/{@code items}/
 * {@code data}）、行键对齐 {@link ImportTargetField} 别名表。<b>响应体字段名未经真实平台验证</b>（无沙箱凭据，
 * 归 ISS-065），fixture 只验证候选探测与行键对齐，不能作为平台侧结构的证据。
 *
 * @author kenyon
 */
class ShopeeProductFetcherContractTest {

    private static final String PARTNER_ID = "pid_1";
    private static final String PARTNER_KEY = "pkey_secret";
    private static final String ACCESS_TOKEN = "atk_live_never_echoed";
    private static final String SHOP_ID = "shop_9";

    /**
     * 第一页：{@code item} 键、一件带两变体的商品、{@code has_next_page=true} 且给出 {@code next_offset}
     */
    private static final String PAGE1_FIXTURE = """
            {"request_id":"p1","error":"","message":"","response":{
              "item":[{
                "item_id":123456,"item_name":"Classic Tee","description":"100% cotton","category_name":"Apparel",
                "price":"19.90","currency":"SGD","weight":"0.2","item_status":"NORMAL",
                "variations":[{"model_id":1,"sku":"TEE-S","name":"Small"},
                              {"model_id":2,"sku":"TEE-M","name":"Medium","price":"20.90"}]
              }],
              "has_next_page":true,"next_offset":1}}""";

    /**
     * 第二页：数组这里叫 {@code items}（另一候选键），且没有变体——一件商品只出一行
     */
    private static final String PAGE2_FIXTURE = """
            {"request_id":"p2","error":"","message":"","response":{
              "items":[{"item_id":777,"item_name":"Hoodie","price":"39.90","item_status":"NORMAL"}],
              "has_next_page":false}}""";

    private static final String EMPTY_FIXTURE = """
            {"request_id":"p0","error":"","message":"","response":{"item":[],"has_next_page":false}}""";

    private HttpServer server;
    private String storeUrl;
    private final List<Recorded> recorded = new ArrayList<>();
    private final Deque<String> scripted = new ArrayDeque<>();

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
    void shouldPostToProductNamespaceWithRequiredBodyParams() {
        scripted.add(EMPTY_FIXTURE);

        fetcher().fetch(productRequest(null, 50), credential());

        Map<String, Object> body = PlatformJson.object(recorded.get(0).body);
        // §6：路径在 product/ 下而不是 item/；必填 offset/page_size/item_status[] 都在 JSON body
        assertTrue(recorded.get(0).path.endsWith("product/get_item_list"), recorded.get(0).path);
        assertEquals(0, ((Number) body.get("offset")).intValue());
        assertEquals(50, ((Number) body.get("page_size")).intValue());
        assertEquals(List.of("NORMAL"), body.get("item_status"), "§6：item_status[] 必填，取最小可辩护在售集（ISS-065）");
    }

    @Test
    void shouldClampPageSizeTo100WhenRequestedLimitIsLarger() {
        scripted.add(EMPTY_FIXTURE);

        fetcher().fetch(productRequest(null, 5000), credential());

        Map<String, Object> body = PlatformJson.object(recorded.get(0).body);
        assertEquals(100, ((Number) body.get("page_size")).intValue(), "§6：商品 page_size 上限 100，超出的压回来");
    }

    @Test
    void shouldChainNextOffsetIntoTheFollowingPage() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 50), credential());

        assertEquals(2, recorded.size(), "has_next_page 为真就跟着 next_offset 翻下一页");
        Map<String, Object> secondBody = PlatformJson.object(recorded.get(1).body);
        assertEquals(1, ((Number) secondBody.get("offset")).intValue(), "下一页 offset 来自响应 next_offset");
        assertEquals(3, rows.size(), "第一页两变体 + 第二页一件无变体商品 = 三行");
    }

    @Test
    void shouldStopPagingWhenTheOffsetDoesNotAdvance() {
        // has_next_page 恒真，但 next_offset 与本页所用 offset 相同 → 不推进，收手而不是空转到上限
        for (int i = 0; i < 10; i++) {
            scripted.add("""
                    {"error":"","response":{"item":[{"item_id":1,"item_name":"X","item_status":"NORMAL"}],
                     "has_next_page":true,"next_offset":0}}""");
        }

        List<Map<String, Object>> rows = fetcher(20).fetch(productRequest(null, 1000), credential());

        assertEquals(1, recorded.size(), "游标不推进就收手，不能翻满页数上限");
        assertEquals(1, rows.size());
    }

    @Test
    void shouldExpandVariationsIntoOneRowEachAndKeepProductLevelFields() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 50), credential());

        Map<String, Object> small = rows.get(0);
        Map<String, Object> medium = rows.get(1);
        assertEquals("123456", small.get("external_id"));
        assertEquals("123456", medium.get("external_id"), "同件商品的两变体共享商品级 ID");
        assertEquals("Classic Tee", small.get("title"));
        assertEquals("TEE-S", small.get("sku"));
        assertEquals("TEE-M", medium.get("sku"));
        assertEquals("1", small.get("variant_id"));
        assertEquals("2", medium.get("variant_id"));
        assertEquals("Small", small.get("variant_title"));
        assertEquals("19.90", small.get("price"), "变体没给价就退到商品价");
        assertEquals("20.90", medium.get("price"), "变体给了价就用变体的");
        assertEquals("SGD", small.get("currency"), "商品级币种在变体行保留");
        assertEquals("NORMAL", small.get("active"));
    }

    @Test
    void shouldEmitOneRowForAnItemWithoutVariations() {
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 50), credential());

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("777", row.get("external_id"));
        assertEquals("Hoodie", row.get("title"));
        assertEquals("39.90", row.get("price"));
        assertFalse(row.containsKey("variant_id"), "无变体就不该凭空造变体键");
        assertFalse(row.containsKey("sku"), "商品对象没有 sku 时探不到就留空，而不是断言");
    }

    @Test
    void shouldReadItemsWhenTheResponseArrayIsKeyedAsData() {
        // §6 未核实数组键名：item / items / data 都要能探到
        scripted.add("""
                {"error":"","response":{"data":[{"item_id":900,"item_name":"Cap","item_status":"NORMAL"}],
                 "has_next_page":false}}""");

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 50), credential());

        assertEquals(1, rows.size());
        assertEquals("900", rows.get(0).get("external_id"));
    }

    @Test
    void shouldEmitOnlyKeysTheImportAliasTableRecognises() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 50), credential());

        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                assertTrue(ImportTargetField.detectByAlias(key).isPresent(),
                        "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
            }
            assertFalse(row.containsKey("item_id"), "不得把平台原始键直接抛给导入侧");
            assertFalse(row.containsKey("variations"));
        }
    }

    @Test
    void shouldFilterRowsByKeywordAfterFetching() {
        scripted.add(PAGE1_FIXTURE);
        scripted.add(PAGE2_FIXTURE);

        List<Map<String, Object>> rows = fetcher().fetch(productRequest("hood", 50), credential());

        assertEquals(1, rows.size());
        assertEquals("777", rows.get(0).get("external_id"));
    }

    @Test
    void shouldFailWithPlatformMessageWhenEnvelopeErrorIsNonEmpty() {
        scripted.add("""
                {"request_id":"r","error":"error_item_not_found","message":"no such item"}""");

        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(productRequest(null, 5), credential()));

        assertEquals("PLATFORM_REQUEST_FAILED", error.getErrorCode());
        assertTrue(error.getMessage().contains("error_item_not_found"), error.getMessage());
        assertTrue(error.getMessage().contains("no such item"), error.getMessage());
        assertFalse(error.getMessage().contains(ACCESS_TOKEN), "异常消息不得回显令牌: " + error.getMessage());
    }

    @Test
    void shouldRequireCredentialForTheSingleArgumentOverload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(productRequest(null, 5)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty());
    }

    // ---------- 测试装置 ----------

    private ShopeeProductFetcher fetcher() {
        return fetcher(20);
    }

    private ShopeeProductFetcher fetcher(int maxPages) {
        return new ShopeeProductFetcher(api(maxPages));
    }

    private ShopeeOpenApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new ShopeeOpenApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.SHOPEE, "tenant-1", "SHOPEE", storeUrl,
                PARTNER_ID, PARTNER_KEY, ACCESS_TOKEN, null, null, "rt_test", null, SHOP_ID, null);
    }

    private static PlatformProductFetchRequest productRequest(String keyword, Integer limit) {
        return new PlatformProductFetchRequest("tenant-1", "SHOPEE", null, null, null, keyword, limit, 1);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.path = exchange.getRequestURI().getPath();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recorded.add(entry);

        String body = scripted.isEmpty() ? EMPTY_FIXTURE : scripted.poll();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class Recorded {
        private String path;
        private String body;
    }
}
