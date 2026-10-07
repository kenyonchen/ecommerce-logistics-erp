package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mercado Libre 传输层：CBT 跨境接口的 base、{@code Authorization: Bearer} 头与订单搜索 URL 组装。
 * <p>
 * 形状由 docs/spec/platform-api-reference.md §7 的三条核实结论决定：
 * <ul>
 *   <li>认证头是标准 OAuth2 的 {@code Authorization: Bearer <token>}（§7）；令牌由
 *       {@link MercadoTokenExchange} 用一次性刷新令牌换取（示例 {@code expires_in:10800}，实际以响应为准）
 *       后经 {@code PlatformCredentials#resolve} 回填，本类只消费 {@code accessToken}：
 *       缺失说明令牌交换没跑过，必须喊出来而不是静默降级（同 {@code AmazonSpApi}）。</li>
 *   <li>CBT 订单走 {@code GET /marketplace/orders/search}，参数 {@code seller.id}（= 卖家 user_id，
 *       即集成的 {@code externalShopId}）、{@code limit}、{@code offset}、
 *       {@code date_created.gte|lt}、{@code last_updated.gte|lt}、{@code sort}+{@code order}，
 *       响应信封 {@code {results[], paging{total,offset,limit}}}（§7）。</li>
 *   <li>时间界是<b>带 UTC 偏移的 ISO-8601 字符串</b>（§7 明文），所以 {@link #offsetTimestamp} 写出
 *       {@code +00:00} 这种数字偏移，既不用裸 {@code LocalDateTime}（Ozon 同族的坑），也不依赖
 *       {@code Z} 这种「算不算带偏移」要解释的写法（细节见 {@link #offsetTimestamp}）。</li>
 * </ul>
 * <p>
 * <b>查询串自己拼、不走 {@code UriComponentsBuilder}</b>：参数键含 {@code .}（{@code seller.id}、
 * {@code last_updated.gte}），值含 {@code +}（偏移量 {@code +00:00}）与 {@code :}；而 {@code +} 在 query 里
 * 是合法字符，{@code build().encode()} 不会把它转义，接收端按表单口径解码就会把偏移读成空格，
 * 时间界随之被平台判非法。故键与值统一 {@link URLEncoder#encode} 后交给
 * {@code PlatformHttpExchange} 的 {@code URI} 重载（该重载不做模板展开，不会被二次编码），
 * 与 {@code MagentoRestApi} 同一处置。
 * <p>
 * <b>本包不提供 {@code PlatformProductFetcher}，也不提供 {@code PlatformFulfillmentPusher}，两者都是有意的 omission 而非遗漏</b>：
 * §7 只核实到商品入口 {@code GET /users/{user_id}/items/search} <b>返回的是 item id 列表</b>，
 * 其 {@code limit} 上限与「逐个取 item 详情」的读路径都写在未核实清单里（{@code offset} 只到约 1000、
 * 更多要用 {@code search_type=scan}+{@code scroll_id}）——拿不到标题、SKU、价格、库存任何一项的商品端口
 * 只会产出一行行空字段，导入侧的字段映射与必填校验（{@code OrderImportTargetField} 的
 * {@code ITEM_SKU}/{@code ITEM_NAME}）无从落地，因此不实现而不是臆造详情端点。
 * 发货侧同理：§7 只核实到两个<b>读</b>端点（{@code GET /marketplace/shipments/{id}}，须带
 * {@code x-format-new: true}；{@code GET /shipments/{id}/orders}），
 * <b>卖家自发货上传运单号的写端点未核实</b>，所以没有 pusher，请勿「顺手补上」而编造 POST 路径。
 * 同一个未核实面还有一层后果：CBT 订单搜索的<b>订单对象字段</b>与<b>逐单明细端点</b>都没核实到，
 * 因此 {@link MercadoOrderFetcher} 的行里明细与地址可能为空——这类行会被导入侧
 * {@code OrderFieldMappingService.validateRequired} 的「订单明细为空 / 收件人为空 / 详细地址为空」拦下，
 * 属于预期行为（宁可整行报错，不要静默出一张缺明细的单），真实响应取证后在 {@code toRow} 一处对齐即可修复。
 * 国内版（非 CBT）的 {@code /orders/search?seller_id=&from=&to=&status=} 形态同样未核实，本类不提供对应路径。
 *
 * @author kenyon
 */
@Component
public class MercadoApi {

    /**
     * §7：唯一的鉴权形状是 {@code Authorization: Bearer <token>}
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 外呼标签，用于 {@code PlatformHttpExchange} 的退避/错误日志（不含凭据）
     */
    public static final String LABEL = "Mercado Libre 订单接口";

    /**
     * §7 核实的 CBT 订单搜索端点
     */
    public static final String ORDERS_SEARCH_PATH = "/marketplace/orders/search";

    /**
     * §7：令牌与 marketplace 接口共用的官方 host 字面值
     */
    public static final String DEFAULT_BASE = "https://api.mercadolibre.com";

    /**
     * §7 参数原名：卖家 user_id（集成的 {@code externalShopId}）
     */
    public static final String SELLER_ID_PARAM = "seller.id";

    /**
     * §7 参数原名：单页条数
     */
    public static final String LIMIT_PARAM = "limit";

    /**
     * §7 参数原名：跳过记录数（本适配器按「每页条数的倍数」推进，见 {@link MercadoOrderFetcher}）
     */
    public static final String OFFSET_PARAM = "offset";

    /**
     * §7 核实的可过滤字段名；时间界的参数键是它加 {@code .gte}/{@code .lt} 后缀
     */
    public static final String LAST_UPDATED_FIELD = "last_updated";

    /**
     * §7 参数原名：更新时间下界（含）
     */
    public static final String LAST_UPDATED_GTE_PARAM = LAST_UPDATED_FIELD + ".gte";

    /**
     * §7 参数原名：更新时间上界（不含）
     */
    public static final String LAST_UPDATED_LT_PARAM = LAST_UPDATED_FIELD + ".lt";

    /**
     * §7 参数原名：排序字段。它接受哪些字段名未被核实，这里复用同一份已核实的过滤字段 {@code last_updated}，
     * 让 {@code offset} 翻页的次序与增量窗口一致，而不是引入一个凭空猜的排序键
     */
    public static final String SORT_PARAM = "sort";

    /**
     * §7 参数原名：排序方向；取值枚举未被核实，本适配器固定用与增量窗口同向的升序（见 {@code MercadoOrderFetcher}）
     */
    public static final String ORDER_PARAM = "order";

    /**
     * 与 {@link #SORT_PARAM} 配套的已核实字段名
     */
    public static final String SORT_FIELD = LAST_UPDATED_FIELD;

    /**
     * §7 核实的响应集合键
     */
    public static final String RESULTS_KEY = "results";

    /**
     * §7 核实的响应分页对象键（其下含 {@code total}/{@code offset}/{@code limit}）
     */
    public static final String PAGING_KEY = "paging";

    /**
     * §7 未给订单搜索的 {@code limit} 上限（只给了 {@code users/{id}/items/search} 的上限未核实），
     * 因此这里<b>不做上限夹取</b>，只给一个保守的默认单页条数；实测到上限后再补夹子。
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * §7 时间界的写出格式：秒恒定写出，偏移按数字 {@code ±HH:MM} 写出（UTC 即 {@code +00:00}），
     * 而不是 {@code ISO_OFFSET_DATE_TIME} 的 {@code Z}。理由见 {@link #offsetTimestamp}。
     */
    private static final DateTimeFormatter QUERY_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(Locale.ROOT);

    private final PlatformHttpExchange http;
    private final int maxPages;

    public MercadoApi(PlatformHttpExchange http,
                      @Value("${platform.mercado.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * {@code GET {base}/marketplace/orders/search?{query}}；{@code query} 的键名是 §7 的平台侧原名。
     */
    public URI ordersUri(PlatformCredential credential, Map<String, String> query) {
        return withQuery(base(credential) + ORDERS_SEARCH_PATH, query);
    }

    /**
     * 带 {@code Authorization: Bearer} 头的只读外呼；429 退避（§7 的 {@code local_rate_limited}）、
     * 5xx 重试与 401/403 立即失败都由 {@code PlatformHttpExchange} 负责。
     */
    public ResponseEntity<String> get(URI uri, PlatformCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(AUTHORIZATION_HEADER, "Bearer " + resolveAccessToken(credential));
        return http.get(uri, headers, LABEL);
    }

    /**
     * §7 的时间界形态：<b>带偏移的 ISO-8601 字符串</b>，写出 {@code 2026-01-25T12:00:00+00:00}。
     * <p>
     * 这里没有复用 {@code PlatformJson.toOffsetQueryTimestamp(value, ZoneOffset.UTC)}：它走
     * {@code ISO_OFFSET_DATE_TIME}，而该格式化器对 UTC 偏移写的是 {@code Z} 而不是 {@code +00:00}
     * （与它自己的注释不符，但它同时服务 Ozon，本包无权改动）。§7 只写了「ISO-8601 带偏移字符串」，
     * 而官方自己的示例时间都是 {@code ±HH:MM} 形态，因此这里用数字偏移显式写出，
     * 免得把「Z 到底算不算带偏移」这种解释风险留给线上。
     * <p>
     * 领域侧的 {@code LocalDateTime} 没有偏移信息，按同步口径统一标注为 UTC（{@code +00:00}）。
     */
    public static String offsetTimestamp(LocalDateTime value) {
        return value == null ? null : QUERY_TIMESTAMP.format(value.atOffset(ZoneOffset.UTC));
    }

    /**
     * 防失控的翻页上限，只用于本包适配器的 {@code offset} 推进保险丝。
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
            throw new BusinessException("Mercado Libre 缺少访问令牌(accessToken)：用一次性刷新令牌换访问令牌的步骤没有执行，"
                    + "请检查 apiKey(client_id)、apiSecret(client_secret) 与 refreshToken 是否都已填写",
                    "PLATFORM_ACCESS_TOKEN_REQUIRED");
        }
        return token.trim();
    }

    /**
     * CBT 订单搜索的 {@code seller.id}：值只能来自集成的 {@code externalShopId}（§2.1 记的就是
     * Mercado {@code user_id}，非敏感标识）。缺失没有任何合理默认值可填，只能拒掉并点名该字段。
     */
    public String sellerId(PlatformCredential credential) {
        String sellerId = credential == null ? null : credential.externalShopId();
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException("Mercado Libre 订单接口要求 " + SELLER_ID_PARAM + "(卖家 user_id)："
                    + "请在集成的 externalShopId 字段填写卖家的数字 user_id，"
                    + "留空会把「不知道查哪个卖家」伪装成「该卖家没有订单」",
                    "PLATFORM_EXTERNAL_SHOP_ID_REQUIRED");
        }
        return sellerId.trim();
    }

    /**
     * 生效 base：{@code storeUrl} 非空时按操作员给的原文整体使用（§7 记「配额按站点(国家)独立」，
     * 站点专用域名走这里覆盖），否则用核实到的官方字面值 {@code https://api.mercadolibre.com}。
     */
    public String base(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        if (storeUrl != null && !storeUrl.isBlank()) {
            return absoluteOverride(storeUrl);
        }
        return DEFAULT_BASE;
    }

    /**
     * 生效 base 的主机名，供连通性测试回显（不含任何凭据）。
     */
    public String host(PlatformCredential credential) {
        return URI.create(base(credential)).getHost();
    }

    /**
     * 单页条数：§7 未给订单搜索的上限，故只做「没给就用默认值 / 下限 1」的收敛，不夹上限。
     */
    public static int pageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return requested;
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

    private String absoluteOverride(String storeUrl) {
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Mercado Libre 的店铺地址(storeUrl)无法解析: " + storeUrl,
                    "PLATFORM_STORE_URL_INVALID");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme()))) {
            throw new BusinessException("Mercado Libre 的店铺地址(storeUrl)要填带协议的完整绝对地址（http/https），"
                    + "用于按国家站点覆盖 host；不需要覆盖时留空即使用 " + DEFAULT_BASE,
                    "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }
}
