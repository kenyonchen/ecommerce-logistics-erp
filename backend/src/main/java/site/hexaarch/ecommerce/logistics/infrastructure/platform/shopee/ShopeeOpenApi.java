package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shopee Open Platform v2 传输层：签名公共参数拼进 query、业务参数放 JSON body、{@code x-env-type} 头切沙箱/生产。
 * <p>
 * 形状由 §6 的三条硬约束决定（docs/spec/platform-api-reference.md §6）：
 * <ul>
 *   <li>公共参数（{@code partner_id/timestamp/access_token/shop_id/sign}）一律放 <b>query string</b>，
 *       业务参数（时间窗、cursor、page_size、order_sn_list 等）放 <b>JSON body</b>；这与 Ozon/eBay 把过滤条件
 *       也塞 query 的写法不同，别混；</li>
 *   <li><b>沙箱与生产共用同一 {@code access_token}，靠请求头 {@code x-env-type}（{@code sandbox}/{@code production}）切换</b>，
 *       不是换 host——所以 host 覆盖与沙箱开关是两回事；</li>
 *   <li>外层信封 {@code {request_id, error, message, warning, response}}，<b>{@code error} 为空即成功</b>
 *       （{@link #readEnvelope}），平台常在 HTTP 200 上回业务错误，光看状态码会误判。</li>
 * </ul>
 * host：默认生产 {@code https://partner.shopeemobile.com}；{@code storeUrl} 非空时整体覆盖（§6 另列
 * {@code openplatform.shopee.cn}、{@code openplatform.shopee.com.br} 两个区域 host，但 {@code region} 字段
 * 在本协议下是 {@code sandbox|production} 的环境开关而不是国家，故<b>不</b>按 region 自动选 host，
 * 区域 host 交给操作员填进 {@code storeUrl}）。签名串里没有国家段，选错 host 只会连不上而不会静默拿错数据。
 * <p>
 * <b>不做 {@code PlatformFulfillmentPusher} 是有意的</b>：§6 记 {@code POST /api/v2/logistics/ship_order} 必填
 * {@code order_sn}，但「除 {@code order_sn} 外还要哪些字段」取决于 {@code get_shipping_parameter} 响应的
 * {@code info_needed}（{@code pickup}/{@code dropoff}/{@code non_integrated} 三选一），而该响应的结构 §6 未核实。
 * 在没有这份结构前，pusher 要么发出缺字段的 body 被平台拒，要么臆造字段——两者都不做，宁可不实现这一端口。
 * <p>
 * 签名值、令牌、请求头都可能含敏感信息：本类任何路径都不把它们写进日志或异常消息，缺字段只点名字段（BR-01/BR-02）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopeeOpenApi {

    /**
     * §6：沙箱/生产切换头（值 {@code sandbox}/{@code production}），非 host 段
     */
    public static final String ENV_HEADER = "x-env-type";

    /**
     * §6 核实的默认生产 host（沙箱共用同一 host，仅靠 {@code x-env-type} 区分）
     */
    public static final String DEFAULT_BASE = "https://partner.shopeemobile.com";

    public static final String AUTH_ACCESS_TOKEN_PATH = "/api/v2/auth/access_token/get";
    public static final String ORDER_LIST_PATH = "/api/v2/order/get_order_list";
    public static final String ORDER_DETAIL_PATH = "/api/v2/order/get_order_detail";
    public static final String PRODUCT_LIST_PATH = "/api/v2/product/get_item_list";

    /**
     * §6：订单时间窗跨度<b>硬上限 15 天</b>，超过即平台报错，适配器侧必须先夹住
     */
    public static final int MAX_TIME_RANGE_DAYS = 15;

    /**
     * §6：商品列表 {@code page_size} 上限 100
     */
    public static final int MAX_PRODUCT_PAGE_SIZE = 100;

    private final PlatformHttpExchange http;
    private final int maxPages;

    public ShopeeOpenApi(PlatformHttpExchange http,
                         @Value("${platform.shopee.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * Shop 级 JSON POST：算签名、拼公共 query、发 {@code x-env-type} 头、序列化 body，退避与状态码语义交给
     * {@code PlatformHttpExchange}。调用方拿到的字符串仍可能带业务 {@code error}，需过 {@link #readEnvelope}。
     */
    public ResponseEntity<String> postJson(PlatformCredential credential, String apiPath, Map<String, Object> body) {
        String partnerId = requirePartnerId(credential);
        String partnerKey = requirePartnerKey(credential);
        String shopId = requireShopId(credential);
        String accessToken = requireAccessToken(credential);
        long timestamp = PlatformJson.toEpochSeconds(LocalDateTime.now(ZoneOffset.UTC));
        String sign = ShopeeSignature.sign(partnerKey,
                ShopeeSignature.baseString(partnerId, apiPath, timestamp, accessToken, shopId));
        URI uri = UriComponentsBuilder.fromUriString(base(credential)).path(apiPath)
                .queryParam("partner_id", partnerId)
                .queryParam("timestamp", timestamp)
                .queryParam("access_token", accessToken)
                .queryParam("shop_id", shopId)
                .queryParam("sign", sign)
                .build().toUri();
        return http.postJson(uri, envHeaders(credential), PlatformJson.write(body), "Shopee Open API " + apiPath);
    }

    /**
     * {@code {base}{apiPath}?partner_id=&timestamp=&sign=}（Public 级签名，§6：不拼 token）——令牌交换端点专用，
     * 因为彼时尚无 {@code access_token} 可签。业务参数（{@code shop_id}/{@code refresh_token}）由调用方放进 body。
     */
    public URI publicLevelUri(PlatformCredential credential, String apiPath, long timestamp, String sign) {
        return UriComponentsBuilder.fromUriString(base(credential)).path(apiPath)
                .queryParam("partner_id", requirePartnerId(credential))
                .queryParam("timestamp", timestamp)
                .queryParam("sign", sign)
                .build().toUri();
    }

    /**
     * 解出外层信封并校验业务错误：{@code error} 为空（或不存在）即成功，返回整根对象由调用方下钻 {@code response}。
     * {@code error} 非空即失败，抛出带平台 {@code error/message} 的 {@code BusinessException}——错误码不是凭据，可以回显。
     */
    public static Map<String, Object> readEnvelope(String jsonBody, String apiPath) {
        Map<String, Object> root = PlatformJson.object(jsonBody);
        String error = PlatformJson.text(root, "error");
        if (error != null) {
            String message = PlatformJson.text(root, "message");
            throw new BusinessException("Shopee Open API " + apiPath + " 返回错误 error=" + error
                    + (message == null ? "" : " message=" + message), "PLATFORM_REQUEST_FAILED");
        }
        return root;
    }

    /**
     * 外层信封里的业务数据节点 {@code response}；缺失返回空对象而不是抛错，让调用方按候选键探测。
     */
    public static Map<String, Object> response(Map<String, Object> envelope) {
        return PlatformJson.nested(envelope, "response");
    }

    /**
     * 防失控的翻页上限（游标/offset 循环保险丝），与 {@code PlatformFetcherRegistry} 无关。
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * 生效 base：{@code storeUrl} 非空整体覆盖（可含区域 host），否则用 §6 的生产字面值。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl == null || storeUrl.isBlank()) {
            return DEFAULT_BASE;
        }
        return absoluteOverride(storeUrl);
    }

    /**
     * {@code x-env-type} 头：§6 沙箱/生产靠它切换，值取 {@code region}（{@code sandbox}/{@code production}）。
     * region 为<b>空白时省略该头</b>而不是发空串——§6 只承认两个枚举值，空值或国家码都可能被平台判非法；
     * 省略即走平台默认环境，比臆造一个值安全。region 非空但不是这两个值时按原文小发送出（操作员明确设了就用他的），
     * 只在真正空白时省略。
     */
    public Map<String, String> envHeaders(PlatformCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        String region = credential == null ? null : credential.region();
        if (region == null || region.isBlank()) {
            return headers;
        }
        String value = region.trim().toLowerCase();
        if ("sandbox".equals(value) || "production".equals(value)) {
            headers.put(ENV_HEADER, value);
        } else {
            // 区域 host 由 storeUrl 承载；region 填了非环境枚举的值不当作 env 头发出去，避免平台判非法
            log.warn("[Shopee] region={} 不是 x-env-type 的合法取值(sandbox/production)，已省略该头；"
                    + "如需按国家切换 host 请把区域 host 填进 storeUrl", region);
        }
        return headers;
    }

    public String requirePartnerId(PlatformCredential credential) {
        return require(credential == null ? null : credential.apiKey(),
                "apiKey(partner_id)", "open.shopee.com 建应用拿到的 partner_id");
    }

    public String requirePartnerKey(PlatformCredential credential) {
        return require(credential == null ? null : credential.apiSecret(),
                "apiSecret(partner_key)", "建应用拿到的 partner_key/secret，仅用于签名、绝不出网");
    }

    public String requireShopId(PlatformCredential credential) {
        return require(credential == null ? null : credential.externalShopId(),
                "externalShopId(shop_id)", "§6：Shop 级接口的签名与 query 都要带 shop_id");
    }

    public String requireAccessToken(PlatformCredential credential) {
        String token = credential == null ? null : credential.accessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException("Shopee Open API 缺少访问令牌(accessToken)：4 小时有效的访问令牌未回填，"
                    + "请确认已用一次性 refresh_token 换取并落库", "PLATFORM_ACCESS_TOKEN_REQUIRED");
        }
        return token.trim();
    }

    private static String require(String value, String field, String hint) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Shopee Open API 缺少 " + field + "：" + hint, "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return value.trim();
    }

    private static String absoluteOverride(String storeUrl) {
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Shopee Open API 的店铺地址(storeUrl)无法解析: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme()))) {
            throw new BusinessException("Shopee Open API 的店铺地址(storeUrl)要填完整绝对地址，"
                    + "如 https://partner.shopeemobile.com 或区域 host；留空则用默认生产 host: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }
}
