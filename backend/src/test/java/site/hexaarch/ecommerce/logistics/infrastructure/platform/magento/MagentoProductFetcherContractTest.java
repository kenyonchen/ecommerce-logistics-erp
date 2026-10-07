package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

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
 * Magento 商品拉取的契约测试，重点是 §9（docs/spec/platform-api-reference.md）点名的那条坑。
 * <p>
 * {@code condition_type=like} <b>要求值自带通配符</b> {@code *} 或 {@code %}：不自己补的话关键词搜索会
 * 稳定返回空集而平台一个错都不抛，运营看到的就是「店铺没这个商品」。所以这里按字面断言
 * {@code [value]=%tee%}，并且断言操作员已经写过通配符时<b>不再叠加</b>（{@code %tee} 不能变成 {@code %%tee%%}，
 * 那会把「只匹配前缀」的显式意图改掉）。同时校验 {@code field}/{@code value}/{@code condition_type}
 * 各出现一次（§9：少任一个整个条件被静默丢弃）。
 * <p>
 * 另外三条：商品行只用 {@code ImportTargetField} 认得的别名；{@code status} 的 1/0 要桥成布尔的
 * {@code active}、没这个键时<b>不透出</b> {@code active}（把停售商品当在售卖比留空更危险）；
 * configurable 商品只出一行、不展开变体（§9 未核实 {@code extensions.product_links} 的形状）。
 * <p>
 * fixture 里的商品对象键名不是平台侧结构的证据（§9 核实的是端点与 {@code searchCriteria} 形态，
 * 字段名待实测校正，ISS-065），这里验证的是候选探测与桥接逻辑本身。
 *
 * @author kenyon
 */
class MagentoProductFetcherContractTest {

    private static final String PASTED_TOKEN = "pasted-integration-token-never-echoed";
    private static final String LIKE_TRIPLE = "searchCriteria[filter_groups][0][filters][0]";
    private static final String EMPTY_PRODUCTS = "{\"items\":[],\"total_count\":0}";

    /**
     * 三行：在售 configurable（带未展开的 product_links）、停售 simple、没给 status 的 simple。
     * 价格与重量都是 JSON 数字，用来验证小数不被截断
     */
    private static final String PRODUCTS_FIXTURE = """
            {"items":[
              {"id":4711,"sku":"TEE-BLK","name":"Classic Tee","type_id":"configurable",
               "price":19.99,"weight":1.5,"status":1,
               "extensions":{"product_links":[{"product_sku":"TEE-BLK-S","link_type":"configurable"}]}},
              {"id":4712,"sku":"HOOD-1","name":"Hoodie","type_id":"simple","status":0},
              {"id":4713,"sku":"MUG-1","name":"Mug","price":0}
            ],"search_criteria":[],"total_count":3}""";

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

    // ---------- like 通配符 ----------

    @Test
    void shouldAppendTheWildcardItselfBecauseLikeMatchesNothingWithoutOne() {
        scripted.add(new Scripted(200, EMPTY_PRODUCTS));

        fetcher().fetch(productRequest("tee", 10), credential());

        String query = query(0);
        assertEquals("/rest/default/V1/products", recorded.get(0).path);
        assertTrue(query.contains(LIKE_TRIPLE + "[field]=name"), query);
        assertTrue(query.contains(LIKE_TRIPLE + "[value]=%tee%"),
                "§9：like 的值必须自带通配符，否则稳定返回空集且不报错: " + query);
        assertTrue(query.contains(LIKE_TRIPLE + "[condition_type]=like"), query);
        assertEquals(1, count(query, "[field]"), query);
        assertEquals(1, count(query, "[value]"), query);
        assertEquals(1, count(query, "[condition_type]"), query);
    }

    @Test
    void shouldKeepAnExplicitlyWildcardedKeywordInsteadOfDoublingTheWildcard() {
        scripted.add(new Scripted(200, EMPTY_PRODUCTS));
        scripted.add(new Scripted(200, EMPTY_PRODUCTS));

        fetcher().fetch(productRequest("%tee", 10), credential());
        fetcher().fetch(productRequest("hood*", 10), credential());

        // 操作员显式写的匹配意图不能被改掉：这里既不再补通配符，也不叠成两层通配符
        assertTrue(query(0).contains(LIKE_TRIPLE + "[value]=%tee"), query(0));
        assertTrue(query(1).contains(LIKE_TRIPLE + "[value]=hood*"), query(1));
        for (int index = 0; index < recorded.size(); index++) {
            assertFalse(query(index).contains("[value]=%%"), "通配符被叠了两层: " + query(index));
        }
    }

    @Test
    void shouldSendNoFilterGroupsWhenNoKeywordWasRequested() {
        scripted.add(new Scripted(200, EMPTY_PRODUCTS));

        fetcher().fetch(productRequest("   ", 10), credential());

        String query = query(0);
        assertEquals("GET", recorded.get(0).method, "商品拉取是只读的");
        assertEquals("", recorded.get(0).body, "只读调用不带请求体");
        assertFalse(query.contains("filter_groups"), "空的 like 会把全部商品筛掉: " + query);
        assertTrue(query.contains("searchCriteria[pageSize]=10"), query);
        assertTrue(query.contains("searchCriteria[currentPage]=1"), query);
    }

    @Test
    void shouldTrimTheKeywordAndLeaveABlankOneAlone() {
        assertEquals("%tee%", MagentoProductFetcher.likeValue("  tee "));
        assertEquals("tee%", MagentoProductFetcher.likeValue("tee%"), "已有通配符就不补");
        assertEquals("tee*", MagentoProductFetcher.likeValue("tee*"));
        assertNull(MagentoProductFetcher.likeValue("  "));
    }

    // ---------- 行映射 ----------

    @Test
    void shouldMapProductsOntoImportAliasesAndBridgeTheStatusSwitch() {
        scripted.add(new Scripted(200, PRODUCTS_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 10), credential());

        assertEquals(3, rows.size(), rows.toString());
        Map<String, Object> configurable = rows.get(0);
        assertEquals("4711", configurable.get("external_id"));
        assertEquals("Classic Tee", configurable.get("title"));
        assertEquals("TEE-BLK", configurable.get("sku"));
        assertEquals("configurable", configurable.get("product_type"));
        assertEquals("19.99", configurable.get("price"), "price 是 JSON 数字，不能被截成 19");
        assertEquals("1.5", configurable.get("weight"));
        assertEquals(true, configurable.get("active"));

        assertEquals(false, rows.get(1).get("active"), "status=0 是停售，不能漏判成在售");
        assertFalse(rows.get(2).containsKey("active"), "没探到 status 就不透出，而不是硬判成启用");
        assertFalse(rows.get(1).containsKey("price"), "探不到的键不透出，导入侧才会把它当空而不是当 0");
        assertEquals("0", rows.get(2).get("price"));
    }

    @Test
    void shouldNotInventVariationRowsOrPlatformKeys() {
        scripted.add(new Scripted(200, PRODUCTS_FIXTURE));

        List<Map<String, Object>> rows = fetcher().fetch(productRequest(null, 10), credential());

        assertEquals(3, rows.size(), "§9 未核实 extensions.product_links 的形状，configurable 只出一行");
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                assertTrue(ImportTargetField.detectByAlias(key).isPresent(),
                        "行键 " + key + " 不在导入侧别名表里，会被映射阶段丢弃");
            }
            assertFalse(row.containsKey("extensions"), "不得把平台原始键直接抛给导入侧");
            assertFalse(row.containsKey("type_id"));
            assertFalse(row.containsKey("custom_attributes"));
            assertFalse(row.containsKey("description"), "description 挂在未核实的 custom_attributes 上，不猜");
        }
    }

    @Test
    void shouldBridgeOnlyTheDocumentedStatusSwitchValues() {
        assertEquals(Boolean.TRUE, MagentoProductFetcher.bridgeActive(1));
        assertEquals(Boolean.FALSE, MagentoProductFetcher.bridgeActive(0));
        assertNull(MagentoProductFetcher.bridgeActive(null));
    }

    @Test
    void shouldPageByTotalCountAndHonourTheStoreViewFromRegion() {
        scripted.add(new Scripted(200, "{\"items\":[{\"id\":1,\"sku\":\"A\"}],\"total_count\":2}"));
        scripted.add(new Scripted(200, "{\"items\":[{\"id\":2,\"sku\":\"B\"}],\"total_count\":2}"));

        List<Map<String, Object>> rows = new MagentoProductFetcher(api(20))
                .fetch(productRequest(null, 10), credential("store"));

        assertEquals(List.of("1", "2"), rows.stream().map(row -> row.get("external_id")).toList());
        assertEquals(2, recorded.size(), recorded.toString());
        assertEquals("/rest/store/V1/products", recorded.get(0).path,
                "region 即 store_view_code（PlatformAuthCatalog 的 MAGENTO 行）");
        assertTrue(query(1).contains("searchCriteria[currentPage]=2"), query(1));
        for (Recorded request : recorded) {
            assertEquals("Bearer " + PASTED_TOKEN, request.header("authorization"));
            assertFalse(request.headers.containsKey("x-api-key"), "X-API-Key 是 CUSTOM_SHOP 的约定头");
            assertFalse(request.headers.containsKey("client-id"), "Client-Id 是 Ozon 的头");
        }
    }

    @Test
    void shouldRefuseTheCredentiallessOverloadBecauseTheTokenOnlyComesFromIntegration() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> fetcher().fetch(productRequest("tee", 10)));

        assertEquals("PLATFORM_CREDENTIAL_REQUIRED", error.getErrorCode());
        assertTrue(recorded.isEmpty(), "还没解析出凭据就不该出网");
    }

    // ---------- 测试装置 ----------

    private MagentoProductFetcher fetcher() {
        return new MagentoProductFetcher(api(20));
    }

    private MagentoRestApi api(int maxPages) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new MagentoRestApi(new PlatformHttpExchange(RestClient.builder(), factory, 2, Duration.ofMillis(10)),
                maxPages);
    }

    private PlatformCredential credential() {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                "erp_sync", "admin-pass-never-echoed", PASTED_TOKEN, null, null, null, null, null, null);
    }

    private PlatformCredential credential(String region) {
        return new PlatformCredential(PlatformAuthType.MAGENTO, "tenant-1", "MAGENTO", storeUrl,
                "erp_sync", "admin-pass-never-echoed", PASTED_TOKEN, null, null, null, region, null, null);
    }

    private static PlatformProductFetchRequest productRequest(String keyword, int limit) {
        return new PlatformProductFetchRequest("tenant-1", "MAGENTO", null, null, null, keyword, limit, 1);
    }

    private String query(int index) {
        return URLDecoder.decode(recorded.get(index).query, StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    private void respond(HttpExchange exchange) throws IOException {
        Recorded entry = new Recorded();
        entry.method = exchange.getRequestMethod();
        entry.path = exchange.getRequestURI().getPath();
        entry.query = exchange.getRequestURI().toString();
        entry.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getRequestHeaders().forEach((name, values) -> entry.headers.put(name.toLowerCase(), values.get(0)));
        recorded.add(entry);

        Scripted next = scripted.isEmpty() ? new Scripted(200, EMPTY_PRODUCTS) : scripted.poll();
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
