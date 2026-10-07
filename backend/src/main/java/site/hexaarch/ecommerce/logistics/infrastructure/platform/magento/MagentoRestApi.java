package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Magento / Adobe Commerce REST 传输层：自建部署的 base URL、{@code store_view_code} 路径段与
 * {@code Authorization: Bearer} 头的组装。
 * <p>
 * URL 形态由 §9 规定（docs/spec/platform-api-reference.md §9）：{@code https://<host>/rest/<store-view-code>/<V1 端点>}，
 * {@code store-view-code} 缺省即 {@code default}。两处与其他家不同的地方都在这里兜住：
 * <ul>
 *   <li><b>base URL 只能来自 {@code credential.storeUrl()}</b>：Magento 是自建软件，平台没有内置 host 可退，
 *       所以空白 storeUrl 用专用码 {@code PLATFORM_STORE_URL_REQUIRED} 拒掉（区别 Ozon/eBay 的「留空用内置 host」），
 *       并强制 http/https 绝对地址。测试夹具只能起在明文回环端口上，因此 http 不额外拒绝。</li>
 *   <li><b>令牌按实例自取</b>：{@code MAGENTO} 不在 {@code PlatformAuthType#oauthTokenBased()} 里，
 *       共享的 {@code PlatformCredentials} 刷新层对它不跑，故令牌交给 {@link MagentoAdminToken}
 *       在每次调用开头按需换取（见该类注释为何不做跨调用缓存、为何不实现 2FA 端点）。</li>
 * </ul>
 * 鉴权只有 Bearer 头：{@code X-API-Key}/{@code X-API-Secret} 是 {@code CUSTOM_SHOP} 的自有约定、
 * {@code Client-Id}/{@code Api-Key} 是 Ozon 的，两者都不该出现在这里（契约测试按字面断言）。
 * <p>
 * <b>查询串自己拼、不走 {@code UriComponentsBuilder}</b>：§9 的 {@code searchCriteria} 是大小写敏感的数组语法，
 * 键里带 {@code [} {@code ]}，而 {@code like} 的值要带 {@code %} 通配符——{@code %} 后不跟两位十六进制时
 * {@code java.net.URI} 会直接判「illegal escaped octet」，模板式的构建器在这里是条死路。
 * 所以键与值统一 {@link URLEncoder#encode} 后再交给 {@code PlatformHttpExchange} 的 {@code URI} 重载
 * （该重载不做 URI 模板展开，不会被二次编码）。平台侧与测试侧都按标准 percent-decoding 还原。
 *
 * @author kenyon
 */
@Component
public class MagentoRestApi {

    /**
     * §9：唯一的鉴权形状是 {@code Authorization: Bearer <token>}
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 外呼标签前缀，用于 {@code PlatformHttpExchange} 的退避/错误日志（不含凭据）
     */
    public static final String LABEL = "Magento REST";

    /**
     * §9：URL 的固定段 {@code /rest}
     */
    public static final String REST_SEGMENT = "/rest";

    /**
     * §9：换令牌端点是 {@code PUT /rest/V1/integration/admin/token}，<b>不带 store-view-code</b>
     */
    public static final String ADMIN_TOKEN_PATH = "/V1/integration/admin/token";

    /**
     * §9：订单列表 {@code GET /rest/<store>/V1/orders}
     */
    public static final String ORDERS_PATH = "/V1/orders";

    /**
     * §9：商品列表 {@code GET /rest/<store>/V1/products}
     */
    public static final String PRODUCTS_PATH = "/V1/products";

    /**
     * §9：发货是 {@code POST /rest/<store>/V1/order/<orderId>/ship}——单数 {@code order}，
     * 与订单列表的复数 {@code orders} 不同，写错就是 404
     */
    public static final String ORDER_SEGMENT = "/V1/order/";

    /**
     * §9：store-view-code 缺省字面值
     */
    public static final String DEFAULT_STORE_VIEW = "default";

    /**
     * {@code searchCriteria} 的分页键（§9 数组语法原样，大小写敏感）
     */
    public static final String PAGE_SIZE_KEY = "searchCriteria[pageSize]";

    /**
     * {@code searchCriteria} 的页码键，从 1 起
     */
    public static final String CURRENT_PAGE_KEY = "searchCriteria[currentPage]";

    /**
     * §9 明说「{@code pageSize} 不给则返回全部匹配、没有官方默认值也没有上限」。既然平台不设边界，
     * 这里给一个自选的保守值：单页取满会把店铺全量订单一次性拉回来。这不是平台约束，实测后可调。
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final PlatformHttpExchange http;
    private final int maxPages;

    public MagentoRestApi(PlatformHttpExchange http,
                          @Value("${platform.magento.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * 创建<b>本次调用</b>的令牌持有者。此刻不发任何请求：令牌在第一次真正要用时才换（见 {@link MagentoAdminToken}）。
     */
    public MagentoAdminToken tokenFor(PlatformCredential credential) {
        return new MagentoAdminToken(http, URI.create(base(credential) + REST_SEGMENT + ADMIN_TOKEN_PATH),
                credential == null ? null : credential.accessToken(),
                credential == null ? null : credential.apiKey(),
                credential == null ? null : credential.apiSecret());
    }

    /**
     * 带 Bearer 头的只读外呼；退避与错误语义由 {@code PlatformHttpExchange} 负责。
     */
    public ResponseEntity<String> get(URI uri, MagentoAdminToken token) {
        return http.get(uri, bearerHeaders(token), LABEL + " GET " + uri.getPath());
    }

    /**
     * 带 Bearer 头的 JSON 写外呼（发货回写）。请求体在此序列化，Content-Type 由传输层设置。
     */
    public ResponseEntity<String> postJson(URI uri, MagentoAdminToken token, Map<String, Object> body) {
        return http.postJson(uri, bearerHeaders(token), PlatformJson.write(body), LABEL + " POST " + uri.getPath());
    }

    /**
     * §9 形态的订单列表 URI：{@code {base}/rest/{storeView}/V1/orders?{searchCriteria…}}。
     */
    public URI ordersUri(PlatformCredential credential, Map<String, String> query) {
        return resourceUri(credential, ORDERS_PATH, query);
    }

    /**
     * §9 形态的商品列表 URI：{@code {base}/rest/{storeView}/V1/products?{searchCriteria…}}。
     */
    public URI productsUri(PlatformCredential credential, Map<String, String> query) {
        return resourceUri(credential, PRODUCTS_PATH, query);
    }

    /**
     * §9 的发货 URI：{@code {base}/rest/{storeView}/V1/order/{orderId}/ship}。
     * 订单号做 percent-编码，避免调用方传来的值改写路径结构。
     */
    public URI shipUri(PlatformCredential credential, String orderId) {
        return URI.create(base(credential) + REST_SEGMENT + "/" + storeView(credential)
                + ORDER_SEGMENT + encode(orderId) + "/ship");
    }

    /**
     * 防失控的页数上限，只用于本包适配器的 {@code currentPage} 翻页保险丝。
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * 生效的 REST base（不含 {@code /rest}）：Magento 是自建部署，只认集成的 {@code storeUrl}。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl == null || storeUrl.isBlank()) {
            throw new BusinessException("Magento 是自建软件，REST base 只能来自集成的 storeUrl"
                    + "（形如 https://shop.example.com），平台没有内置 host 可退，当前未填写",
                    "PLATFORM_STORE_URL_REQUIRED");
        }
        return validatedStoreUrl(storeUrl);
    }

    /**
     * 生效 base 的主机名，供连通性测试回显（不含任何凭据）。
     */
    public String host(PlatformCredential credential) {
        return URI.create(base(credential)).getHost();
    }

    /**
     * 生效的 store view code：{@code region} 非空则用之，否则 §9 的 {@code default}。
     * <p>
     * <b>不按 {@code PlatformAuthCatalog} 的 {@code regionOptions}（default/website/store）做白名单校验</b>：
     * 那三个值是「这一栏可以填哪一等 code」的界面提示，而真实填的是部署里定义的 code 字面值
     * （如 {@code default} 之外还有 {@code en_us}、网站 code 等），强制三选一会把正常部署拒掉。
     * 这里只校验它能否安全拼进路径段。
     */
    public String storeView(PlatformCredential credential) {
        String region = credential == null ? null : credential.region();
        if (region == null || region.isBlank()) {
            return DEFAULT_STORE_VIEW;
        }
        String value = region.trim();
        if (value.contains("/") || value.contains("?") || value.contains("#")
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("Magento 的 store view code(region) 不合法：它是 URL 路径段，"
                    + "不能含斜杠/查询符/空白，当前配置无法安全拼进 " + REST_SEGMENT + "/<store-view-code>/ 形态: " + value,
                    "PLATFORM_STORE_VIEW_INVALID");
        }
        return value;
    }

    /**
     * 单页条数：§9 未给默认值与上限，故只按调用方要的总条数收敛，不超过 {@link #DEFAULT_PAGE_SIZE}。
     */
    public static int pageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, DEFAULT_PAGE_SIZE);
    }

    /**
     * 写一个 {@code searchCriteria} 过滤条件（§9 数组语法）。
     * <p>
     * §9 的硬性形状是 {@code field}、{@code value}、{@code condition_type} <b>各出现一次</b>——
     * 少任一个，平台会静默丢掉整个条件而不是报错，「拉不到数据」就是这么来的。
     * 组合语义同样来自 §9：<b>组与组之间是 AND，同一组内的 filters 之间是 OR</b>，
     * 所以「下界 AND 上界」这类需求必须拆到两个 {@code filter_groups}，写进同一组就变成 OR 了。
     */
    public static void putFilter(Map<String, String> query, int groupIndex, int filterIndex,
                                 String field, String value, String conditionType) {
        String prefix = "searchCriteria[filter_groups][" + groupIndex + "][filters][" + filterIndex + "]";
        query.put(prefix + "[field]", field);
        query.put(prefix + "[value]", value);
        query.put(prefix + "[condition_type]", conditionType);
    }

    private URI resourceUri(PlatformCredential credential, String v1Path, Map<String, String> query) {
        return withQuery(base(credential) + REST_SEGMENT + "/" + storeView(credential) + v1Path, query);
    }

    private static Map<String, String> bearerHeaders(MagentoAdminToken token) {
        return Map.of(AUTHORIZATION_HEADER, "Bearer " + token.bearer());
    }

    private static URI withQuery(String path, Map<String, String> query) {
        List<String> pairs = new ArrayList<>();
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    pairs.add(encode(key) + "=" + encode(value));
                }
            });
        }
        return pairs.isEmpty() ? URI.create(path) : URI.create(path + "?" + String.join("&", pairs));
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    /**
     * storeUrl 归一化：补协议、去尾斜杠、要求 http/https 且带主机名。
     */
    private static String validatedStoreUrl(String storeUrl) {
        String url = storeUrl.trim();
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Magento 的店铺地址(storeUrl)无法解析，形如 https://shop.example.com: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase();
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equals(scheme) && !"https".equals(scheme))) {
            throw new BusinessException("Magento 的店铺地址(storeUrl)要填带协议的完整绝对地址（http/https），"
                    + "形如 https://shop.example.com，当前: " + storeUrl, "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }
}
