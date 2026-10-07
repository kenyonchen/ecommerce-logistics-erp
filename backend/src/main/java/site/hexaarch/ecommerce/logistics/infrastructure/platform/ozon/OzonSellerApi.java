package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ozon Seller API 传输层：{@code Client-Id} + {@code Api-Key} 静态头与 JSON POST 组装。
 * <p>
 * 这是本轮 9 家里协议形状最简单的一家（docs/spec/platform-api-reference.md §8）：
 * 只有 {@code https://api-seller.ozon.ru} 一个 host、<b>没有 OAuth、没有刷新、没有签名</b>，
 * 所以本包不提供 {@code PlatformTokenExchange}，凭据直接从 {@code apiKey}/{@code apiSecret}
 * 映射成两个请求头（§8 与 {@code PlatformAuthCatalog} 的 OZON 行同一口径）。
 * <p>
 * 两处必须由代码兜住的坑：
 * <ul>
 *   <li>§8「缺一即 401」——两个头任何一个为空都要在出网前喊出来并点名字段，
 *       而不是让操作员去猜平台为什么拒；头的字面值本身永不进日志与异常消息（BR-01/BR-02）。</li>
 *   <li>§8「强制 https」——{@code storeUrl} 覆盖时明文 {@code http://} 不能被悄悄用出去，
 *       专用错误码 {@code PLATFORM_STORE_URL_INSECURE} 拒绝；仅回环地址例外，因为契约测试的
 *       fixture 服务只能起在 {@code 127.0.0.1} 的明文端口上（真实调用永远走 https）。
 *       没写协议的覆盖按 {@code https://} 补齐而不是拒绝，避免把「填了个裸域名」变成配置错误。</li>
 * </ul>
 * 关于错误语义：Ozon 的 401 响应体是 {@code {code:<int>, message:"…"}}——{@code code} 是数字不是字符串
 * （§8），与 Amazon/eBay 的字符串错误码不同，所以不要按 {@code code} 文本去比对；
 * 退避与这些状态码的处置都由 {@code PlatformHttpExchange} 统一负责（§2.3），本类不重复实现。
 * §8 另记 Api-Key 过期时平台返回 500，因此 500 在本协议下也可能是凭据问题。
 *
 * @author kenyon
 */
@Component
public class OzonSellerApi {

    /**
     * §8 的两个鉴权头字面值，大小写按官方文档原文
     */
    public static final String CLIENT_ID_HEADER = "Client-Id";
    public static final String API_KEY_HEADER = "Api-Key";

    /**
     * §8：唯一的 host，强制 https
     */
    public static final String DEFAULT_BASE = "https://api-seller.ozon.ru";

    public static final String ORDERS_LIST_PATH = "/v3/posting/fbs/list";
    public static final String ORDER_DETAIL_PATH = "/v2/posting/fbs/get";
    public static final String PRODUCT_LIST_PATH = "/v3/product/list";
    public static final String PRODUCT_ATTRIBUTES_PATH = "/v4/product/info/attributes";
    public static final String FULFILLMENT_PATH = "/v2/posts/add";

    /**
     * §8：列表接口的 {@code filter.limit} 上限 1000；{@code /v4/product/info/attributes} 的 {@code limit} 同上限
     */
    public static final int MAX_FILTER_LIMIT = 1000;

    /**
     * §8：{@code /v4/product/info/attributes} 的 {@code limit} 默认 100
     */
    public static final int DEFAULT_ATTRIBUTES_LIMIT = 100;

    private final PlatformHttpExchange http;
    private final int maxPages;

    public OzonSellerApi(PlatformHttpExchange http,
                         @Value("${platform.ozon.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * {@code {base}{path}}：Ozon 的接口全部是 JSON POST，路径里没有路径参数，故只需拼接。
     */
    public URI uri(PlatformCredential credential, String path) {
        return URI.create(base(credential) + path);
    }

    /**
     * 带 {@code Client-Id}/{@code Api-Key} 的 JSON POST；请求体在此序列化，
     * Content-Type 与退避/错误语义由 {@code PlatformHttpExchange} 负责。
     */
    public ResponseEntity<String> postJson(PlatformCredential credential, String path, Map<String, Object> body) {
        return http.postJson(uri(credential, path), headers(credential), PlatformJson.write(body),
                "Ozon Seller API " + path);
    }

    /**
     * 防失控的翻页上限，只用于本包适配器的 {@code offset}/{@code last_id} 循环保险丝。
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * 生效 base：{@code storeUrl} 非空时按操作员给的原文整体使用（可含路径前缀），否则用 §8 的内置 host。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl == null || storeUrl.isBlank()) {
            return DEFAULT_BASE;
        }
        return absoluteOverride(storeUrl);
    }

    /**
     * 当前实际生效的主机名，供连通性测试回显（不含任何凭据）。
     */
    public String host(PlatformCredential credential) {
        return URI.create(base(credential)).getHost();
    }

    /**
     * 单页条数夹到 §8 的上限 1000：未指定或非法时取上限，因为列表接口不给明细，
     * 单页取满反而能减少逐单回取的轮次与请求数。
     */
    public static int clampListLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return MAX_FILTER_LIMIT;
        }
        return Math.min(requested, MAX_FILTER_LIMIT);
    }

    /**
     * 商品属性接口的 {@code limit}：§8 给的默认是 100、上限 1000，未指定时按默认而不是取上限，
     * 因为属性行是宽对象，单页过大容易整页失败。
     */
    public static int clampAttributesLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_ATTRIBUTES_LIMIT;
        }
        return Math.min(requested, MAX_FILTER_LIMIT);
    }

    /**
     * 两个头的值都来自集成配置（{@code apiKey}=Client-Id、{@code apiSecret}=Api-Key，
     * 见 {@code PlatformAuthCatalog} 的 OZON 行），任何一个为空都在这里拒掉，消息只点字段名不回显值。
     */
    private static Map<String, String> headers(PlatformCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(CLIENT_ID_HEADER, require(credential == null ? null : credential.apiKey(), "apiKey(Client-Id)"));
        headers.put(API_KEY_HEADER, require(credential == null ? null : credential.apiSecret(), "apiSecret(Api-Key)"));
        return headers;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Ozon Seller API 缺少 " + field
                    + "：§8 要求 Client-Id 与 Api-Key 两个头同时存在，缺一即 401；"
                    + "Client-Id 填集成的 apiKey、Api-Key 填集成的 apiSecret", "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return value.trim();
    }

    private static String absoluteOverride(String storeUrl) {
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
            throw new BusinessException("Ozon Seller API 的店铺地址(storeUrl)无法解析: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("Ozon Seller API 的店铺地址(storeUrl)要填完整绝对地址，"
                    + "形如 https://api-seller.ozon.ru；不需要覆盖时请留空", "PLATFORM_STORE_URL_INVALID");
        }
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase();
        if ("https".equals(scheme)) {
            return url;
        }
        // §8 强制 https：除契约测试的本地回环 fixture 外，明文覆盖一律拒绝而不是静默用出去
        if ("http".equals(scheme) && isLoopback(host)) {
            return url;
        }
        throw new BusinessException("Ozon Seller API 强制 https（§8：host 只认 https://api-seller.ozon.ru），"
                + "当前 storeUrl 覆盖为不安全的协议: " + url, "PLATFORM_STORE_URL_INSECURE");
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host) || host.startsWith("127.");
    }
}
