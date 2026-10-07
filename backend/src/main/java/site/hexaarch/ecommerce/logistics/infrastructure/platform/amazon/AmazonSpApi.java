package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amazon Selling Partner API 传输层：区域 host 选择、{@code x-amz-access-token} 头与订单列表 URL 组装。
 * <p>
 * 形状由官方文档的两条硬约束决定（docs/spec/platform-api-reference.md §3.1、§3.2）：
 * <ul>
 *   <li>鉴权只有请求头 {@code x-amz-access-token}，<b>没有 SigV4 步骤</b>（官方示例明确「no signing
 *       information」），所以这里不接任何签名器，也不发 {@code Authorization} 头；</li>
 *   <li>三个区域是三套独立 host（{@code sellingpartnerapi-na/-eu/-fe.amazon.com}）；沙箱是同 host 上的
 *       {@code /sandbox} <b>路径前缀</b>，而不是已弃用的 {@code sandbox.sellingpartnerapi-*} 域名，
 *       因此覆盖入口按「绝对 base（可含路径前缀）」处理，绝不能只替换主机名。</li>
 * </ul>
 * 访问令牌由 {@link AmazonLwaTokenExchange} 用刷新令牌换取（有效期 1 小时）后经
 * {@code PlatformCredentials#resolve} 回填，本类只消费 {@code accessToken}：令牌缺失说明令牌交换没有跑过，
 * 此时必须喊出来而不是静默降级——否则凭据配置问题会伪装成「该平台没有订单」。
 *
 * @author kenyon
 */
@Component
public class AmazonSpApi {

    /**
     * SP-API 唯一的鉴权头（§3.2），大小写按官方文档字面值
     */
    public static final String ACCESS_TOKEN_HEADER = "x-amz-access-token";

    /**
     * 订单列表端点（§3.3 的 v0 形态，PascalCase 参数）
     */
    public static final String ORDERS_PATH = "/orders/v0/orders";

    /**
     * 单个请求的 {@code MaxResultsPerPage} 上限（§3.3：1-100）
     */
    public static final int MAX_RESULTS_PER_PAGE_LIMIT = 100;

    /**
     * {@code MarketplaceIds} 的数量上限（§3.3：≤50）
     */
    public static final int MAX_MARKETPLACE_IDS = 50;

    private static final String DEFAULT_REGION = "NA";

    private static final Map<String, String> REGION_HOSTS = Map.of(
            "NA", "https://sellingpartnerapi-na.amazon.com",
            "EU", "https://sellingpartnerapi-eu.amazon.com",
            "FE", "https://sellingpartnerapi-fe.amazon.com");

    private final PlatformHttpExchange http;
    private final int maxPages;

    public AmazonSpApi(PlatformHttpExchange http,
                       @Value("${platform.amazon.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * {@code GET {base}/orders/v0/orders?{query}}；{@code query} 里的键名是平台侧的 PascalCase 原名。
     */
    public URI ordersUri(PlatformCredential credential, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base(credential)).path(ORDERS_PATH);
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
     * 带 {@code x-amz-access-token} 头的只读外呼；退避与错误语义由 {@code PlatformHttpExchange} 负责。
     */
    public ResponseEntity<String> get(URI uri, PlatformCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ACCESS_TOKEN_HEADER, resolveAccessToken(credential));
        return http.get(uri, headers, "Amazon SP-API 订单接口");
    }

    /**
     * 防失控的游标页数上限，与 {@code PlatformFetcherRegistry} 无关，只用于本适配器的 {@code NextToken} 翻页。
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * 访问令牌：缺失即令牌交换没有执行（没配 apiKey/apiSecret/refreshToken，或集成未激活），
     * 消息只点名字段，不回显任何凭据值。
     */
    public String resolveAccessToken(PlatformCredential credential) {
        String token = credential == null ? null : credential.accessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException("Amazon SP-API 缺少访问令牌(accessToken)：LWA 刷新令牌换访问令牌的步骤没有执行，"
                    + "请检查 apiKey(LWA client_id)、apiSecret(LWA client_secret) 与 refreshToken 是否都已填写",
                    "PLATFORM_ACCESS_TOKEN_REQUIRED");
        }
        return token.trim();
    }

    /**
     * 绝对 base：{@code storeUrl} 非空时按操作员给的原文整体使用（沙箱 {@code /sandbox} 前缀就走这里），
     * 否则按 {@code region} 取内置 host；{@code region} 空白按 NA 处理。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl != null && !storeUrl.isBlank()) {
            return absoluteOverride(storeUrl);
        }
        return regionHost(credential == null ? null : credential.region());
    }

    /**
     * 当前实际生效的区域名（用于回显），覆盖 base 时按内置 NA 口径报告。
     */
    public String regionLabel(PlatformCredential credential) {
        String region = credential == null ? null : credential.region();
        if (region == null || region.isBlank()) {
            return DEFAULT_REGION;
        }
        return region.trim().toUpperCase();
    }

    /**
     * {@code MarketplaceIds} 是 v0 订单接口的必填参数（§3.3），集成的 {@code marketplaceIds} 为空时
     * 没有任何合理的默认站点可填，只能拒掉并点名该字段。
     */
    public static String marketplaceIdsParam(PlatformCredential credential) {
        List<String> ids = credential == null ? List.of() : credential.marketplaceIdList();
        if (ids.isEmpty()) {
            throw new BusinessException("Amazon SP-API 订单接口要求 MarketplaceIds(售卖站点ID，如 US 为 ATVPDKIKX0DER)："
                    + "请在集成的 marketplaceIds 字段填写，多个站点用英文逗号分隔", "PLATFORM_MARKETPLACE_IDS_REQUIRED");
        }
        if (ids.size() > MAX_MARKETPLACE_IDS) {
            throw new BusinessException("Amazon SP-API 的 MarketplaceIds 单次请求最多 " + MAX_MARKETPLACE_IDS
                    + " 个站点，当前配置了 " + ids.size() + " 个，请拆分集成记录", "PLATFORM_MARKETPLACE_IDS_INVALID");
        }
        return String.join(",", ids);
    }

    /**
     * 单页条数夹到平台允许的 1-100；请求侧没给就取上限，减少翻页次数（v0 速率只有 0.0167 rps）。
     */
    public static int clampMaxResultsPerPage(Integer requested) {
        if (requested == null || requested <= 0) {
            return MAX_RESULTS_PER_PAGE_LIMIT;
        }
        return Math.min(requested, MAX_RESULTS_PER_PAGE_LIMIT);
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
            throw new BusinessException("Amazon SP-API 的店铺地址(storeUrl)无法解析: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme()))) {
            throw new BusinessException("Amazon SP-API 的店铺地址(storeUrl)要填完整绝对地址，"
                    + "沙箱形如 https://sellingpartnerapi-na.amazon.com/sandbox；不需要填时请留空以按 region 取内置 host",
                    "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }

    private static String regionHost(String region) {
        if (region == null || region.isBlank()) {
            return REGION_HOSTS.get(DEFAULT_REGION);
        }
        String host = REGION_HOSTS.get(region.trim().toUpperCase());
        if (host == null) {
            throw new BusinessException("Amazon SP-API 只支持 NA/EU/FE 三个区域 host，当前 region=" + region
                    + "；请改成三者之一或留空(按 NA)", "PLATFORM_REGION_UNSUPPORTED");
        }
        return host;
    }
}
