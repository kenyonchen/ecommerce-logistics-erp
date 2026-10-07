package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * eBay Sell Fulfillment API 传输层：base 选择、{@code Authorization: Bearer} 头与订单/发货 URL 组装。
 * <p>
 * 形状由官方 OpenAPI spec 的两条硬约束决定（docs/spec/platform-api-reference.md §4.1、§4.2）：
 * <ul>
 *   <li>鉴权是标准 OAuth2 的 {@code Authorization: Bearer <accessToken>} 头（区别于 Amazon 的 {@code x-amz-access-token}）；</li>
 *   <li>servers 现为 {@code https://apiz.ebay.com/sell/fulfillment/v1}，沙箱是同前缀的
 *       {@code https://apiz.sandbox.ebay.com}（§4.1）——因此覆盖入口按「绝对 base（可含路径前缀）」处理，
 *       而不是只替换主机名。</li>
 * </ul>
 * 访问令牌由 {@link EbayOAuthTokenExchange} 用刷新令牌换取后经 {@code PlatformCredentials#resolve} 回填，
 * 本类只消费 {@code accessToken}：缺失说明令牌交换没跑过，必须喊出来而不是静默降级（同 {@code AmazonSpApi}）。
 * <p>
 * <b>本域没有商品/库存列表，故本包不提供 ProductFetcher。</b>
 * §4.2 明确商品在 {@code sell/inventory} 域（{@code GET /inventory_item}、{@code GET /offer}），
 * 要另开 {@code sell.inventory} scope、且与 fulfillment 不同一把——那是本次交付之外的独立适配器，
 * 缺这个 bean 是有意的，不是遗漏，请勿「顺手补上」而臆造端点。
 *
 * @author kenyon
 */
@Component
public class EbaySellApi {

    /**
     * Sell API 的标准鉴权头（§4.1）
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * fulfillment 域版本前缀（§4.1 servers）
     */
    public static final String PATH_PREFIX = "/sell/fulfillment/v1";

    /**
     * 订单列表端点（§4.2 {@code getOrders}）
     */
    public static final String ORDERS_PATH = "/order";

    /**
     * §4.2：{@code limit} 上限 200，超过即报错。
     */
    public static final int MAX_LIMIT = 200;

    /**
     * §4.2：缺省页大小 50；未指定时本类取上限以减少翻页次数。
     */
    public static final int DEFAULT_LIMIT = 50;

    private static final String DEFAULT_BASE = "https://apiz.ebay.com";

    private final PlatformHttpExchange http;
    private final int maxPages;

    public EbaySellApi(PlatformHttpExchange http,
                       @Value("${platform.ebay.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * {@code GET {base}/sell/fulfillment/v1/order?{query}}；{@code query} 里的键名是 spec 的五个参数原名。
     */
    public URI ordersUri(PlatformCredential credential, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base(credential))
                .path(PATH_PREFIX).path(ORDERS_PATH);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build().toUri();
    }

    /**
     * {@code POST {base}/sell/fulfillment/v1/order/{orderId}/shipping_fulfillment}（§4.2）。
     * 订单号用 {@code pathSegment} 逐段编码，防止把 {@code /}、{@code ?} 之类拼进对方 URL。
     */
    public URI fulfillmentUri(PlatformCredential credential, String orderId) {
        return UriComponentsBuilder.fromUriString(base(credential))
                .path(PATH_PREFIX)
                .pathSegment("order", orderId, "shipping_fulfillment")
                .build().toUri();
    }

    /**
     * 带 {@code Authorization: Bearer} 头的只读外呼；退避与错误语义由 {@code PlatformHttpExchange} 负责。
     */
    public ResponseEntity<String> get(URI uri, PlatformCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(AUTHORIZATION_HEADER, bearer(resolveAccessToken(credential)));
        return http.get(uri, headers, "eBay Sell 订单接口");
    }

    /**
     * 带 Bearer 头的 JSON 写外呼（发货回写）。请求体在此序列化，Content-Type 由传输层设置。
     */
    public ResponseEntity<String> postJson(URI uri, PlatformCredential credential, Map<String, Object> body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(AUTHORIZATION_HEADER, bearer(resolveAccessToken(credential)));
        return http.postJson(uri, headers, PlatformJson.write(body), "eBay Sell 发货接口");
    }

    /**
     * 防失控的翻页上限，只用于本适配器跟随响应体 {@code next} 时的保险丝。
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * 访问令牌：缺失即令牌交换没有执行（没配 apiKey/apiSecret/refreshToken，或集成未激活），
     * 消息只点名字段，不回显任何凭据值（同 {@code AmazonSpApi.resolveAccessToken} 的口径）。
     */
    public String resolveAccessToken(PlatformCredential credential) {
        String token = credential == null ? null : credential.accessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException("eBay Sell 缺少访问令牌(accessToken)：OAuth 刷新令牌换访问令牌的步骤没有执行，"
                    + "请检查 apiKey(OAuth appid)、apiSecret(OAuth secret) 与 refreshToken 是否都已填写",
                    "PLATFORM_ACCESS_TOKEN_REQUIRED");
        }
        return token.trim();
    }

    /**
     * 绝对 base：{@code storeUrl} 非空时按操作员给的原文整体使用（沙箱 {@code apiz.sandbox.ebay.com}
     * 或带路径前缀的部署走这里），否则取核实到的生产字面值 {@code https://apiz.ebay.com}。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl != null && !storeUrl.isBlank()) {
            return absoluteOverride(storeUrl);
        }
        return DEFAULT_BASE;
    }

    /**
     * 配置生效 host 的主机名，供订单适配器校验响应体 {@code next} 是否属于本平台。
     */
    public String host(PlatformCredential credential) {
        return URI.create(base(credential)).getHost();
    }

    /**
     * {@code marketplaceId} 解析助手：返回集成的第一个市场标识（如 {@code EBAY_US}），空白时为 {@code null}。
     * <p>
     * <b>本方法没有被订单列表调用，且这是刻意的</b>：§4.2 核实到 {@code GET /order} 的 parameters 只有
     * {@code fieldGroups|filter|limit|offset|orderIds} 五个，§4.3 进一步记「spec 无 {@code marketplaceId} 路径参数」，
     * 所以往订单列表塞 {@code marketplaceId} 等于发一个 spec 不认的参数。此处保留助手仅因 §4.3 把「各市场共用
     * {@code api.ebay.com}」列为未核实——若实测某端点确需市场标识，这里是唯一入口，届时再接线而不是臆造。
     */
    public String marketplaceId(PlatformCredential credential) {
        List<String> ids = credential == null ? List.of() : credential.marketplaceIdList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 单页条数夹到 spec 允许的 1..200（§4.2）：未指定或非法时取上限 {@code 200}，超过 200 会被压回 200，
     * 因为发 &gt;200 平台直接报错。
     */
    public static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return MAX_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String absoluteOverride(String storeUrl) {
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("eBay Sell 的店铺地址(storeUrl)无法解析: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme()))) {
            throw new BusinessException("eBay Sell 的店铺地址(storeUrl)要填完整绝对地址，"
                    + "沙箱形如 https://apiz.sandbox.ebay.com；不需要覆盖时请留空以使用生产 host",
                    "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }
}
