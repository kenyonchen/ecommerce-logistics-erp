package site.hexaarch.ecommerce.logistics.infrastructure.platform.aliexpress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformSignatures;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 速卖通 TOP 网关（与淘宝同源）传输层：组装公共参数、用 {@code app_secret} 签名、POST 表单调用、按 §5.1 的信封解包。
 * <p>
 * 形状完全由 docs/spec/platform-api-reference.md §5 决定：
 * <ul>
 *   <li>网关默认 {@code gw.api.taobao.com/router/rest}，{@code storeUrl} 可整体覆盖（§5 另列海外
 *       {@code eco.taobao.com} / {@code api.taobao.com}）；本类<b>固定取 {@code https} 方案</b>——
 *       §5.1 同时给了 {@code http} 与 {@code https} 两个 scheme，而 {@code session} 是卖家授权令牌明文，
 *       走 {@code http} 会让它在链路上裸奔（签名只保护参数不被篡改，不加密内容），故默认 https 更安全且
 *       不影响 §5 记录的 host/path；需要显式 http 时把完整 http 地址填进 {@code storeUrl}。</li>
 *   <li>公共参数：{@code method}、{@code app_key}(=apiKey)、{@code session}(=accessToken)、
 *       {@code timestamp}（{@code yyyy-MM-dd HH:mm:ss}）、{@code v=2.0}、{@code sign_method}、{@code sign}、
 *       {@code format}（§5.1）。</li>
 *   <li>签名：{@code md5}/{@code hmac}/{@code hmac-sha256} 三种，{@code sign} 自身不参与，其余参数按 key
 *       字典序拼 {@code k+v}，{@code md5} 分支用 {@code secret+串+secret} 取 32 位大写、{@code hmac} 分支不包
 *       secret——这些拼接与大小写规则由 {@link PlatformSignatures#topSign} 实现，本类不重复实现（§5.1）。</li>
 *   <li><b>正式调用必须 POST</b>（§5.1：GET 仅调试），业务参数 URL 编码后放 body；故这里用
 *       {@link PlatformHttpExchange#postForm} 而非 GET。</li>
 *   <li>成功信封 {@code {"aliexpress_trade_seller_orderlist_get_response":{…}}}，失败信封
 *       {@code {"error_response":{msg,code,sub_msg,sub_code}}}（§5.1）——两者都可能在 HTTP 200 上返回，
 *       所以解包必须看 body 而不能只看状态码。</li>
 * </ul>
 * 关于 {@code timestamp} 的时区：§5.1 核实的是<b>格式</b>（{@code yyyy-MM-dd HH:mm:ss}），没有核实参考时区，
 * 本类按 JVM 墙上时钟发出；时钟偏差或时区设错会以网关 {@code sub_code} 形式回传，由调用方据此排查，不在此臆测偏移。
 * <p>
 * <b>本适配器刻意不实现 {@code PlatformProductFetcher} 与 {@code PlatformFulfillmentPusher}，这是决定而非遗漏</b>
 * （§5.2）：
 * <ul>
 *   <li>商品列表：公开可核实的只有<b>联盟</b>接口 {@code aliexpress.traffic.product.search}，它需要单独权限包且
 *       返回<b>平台推荐商品而非卖家自有商品目录</b>；卖家自有商品列表方法要登录 {@code open.aliexpress.com} 才可见。
 *       用联盟接口冒充卖家目录会导入错误的商品集，故不实现。</li>
 *   <li>发货回写：§5.2 记到了方法名 {@code aliexpress.logistics.order.shipment} / 备选
 *       {@code aliexpress.solution.order.fulfill}，但<b>没有记它们的入参对象名</b>（只给了备选方法的部分必填标量
 *       {@code _service_name}/{@code _out_ref}/{@code _send_type}/{@code _logistics_no}）。发货是写操作，
 *       参数名靠猜要么被网关拒、要么把正确的单发错，两者代价都高于「暂不回写」，故不实现。</li>
 * </ul>
 * 凭据（{@code app_key}/{@code app_secret}/{@code session}）、签名值 {@code sign} 都属敏感：本类任何路径都不把它们
 * 写进日志、异常消息或 {@code toString}，缺字段只点名字段（BR-01/BR-02）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AliexpressTopClient {

    /**
     * §5.1 默认网关（本类按 https 方案取，理由见类注释）；{@code storeUrl} 非空时整体覆盖
     */
    public static final String DEFAULT_GATEWAY = "https://gw.api.taobao.com/router/rest";

    /**
     * §5.2 卖家订单列表方法名（明确「不是 {@code sellers.orders.list}」）
     */
    public static final String ORDER_LIST_METHOD = "aliexpress.trade.seller.orderlist.get";

    /**
     * §5.1 公共参数 {@code v} 的固定取值
     */
    static final String API_VERSION = "2.0";

    /**
     * §5.1 公共参数 {@code format} 取值：取 JSON 信封便于解析
     */
    static final String FORMAT = "json";

    /**
     * §5.1 核实的 {@code timestamp} 与订单时间窗参数格式；订单适配器复用同一份格式串。
     */
    public static final DateTimeFormatter TOP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformHttpExchange http;
    private final String signMethod;
    private final int maxPages;

    public AliexpressTopClient(PlatformHttpExchange http,
                               @Value("${platform.aliexpress.sign-method:md5}") String signMethod,
                               @Value("${platform.aliexpress.cursor.max-pages:20}") int maxPages) {
        this.http = http;
        this.signMethod = signMethod == null || signMethod.isBlank() ? "md5" : signMethod.trim().toLowerCase(Locale.ROOT);
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * 一次 TOP 调用：校验凭据→拼公共参数→并业务参数→签名→POST 表单→按 §5.1 信封解包。
     * 返回的是成功信封里被解出的业务对象（如 {@code {..._response}} 内层）；{@code error_response} 直接抛
     * {@code PLATFORM_TOP_ERROR}。{@code businessParams} 的键是平台侧原名（如 {@code param_aeop_order_query}），
     * 值是已序列化好的 JSON 字符串。
     */
    public Map<String, Object> execute(PlatformCredential credential, String method, Map<String, String> businessParams) {
        if (method == null || method.isBlank()) {
            throw new BusinessException("AliExpress TOP 调用缺少 method", "PLATFORM_REQUEST_INVALID");
        }
        String appKey = requireAppKey(credential);
        String appSecret = requireAppSecret(credential);
        String session = requireSession(credential);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", method.trim());
        params.put("app_key", appKey);
        params.put("session", session);
        params.put("timestamp", LocalDateTime.now().format(TOP_DATE_FORMAT));
        params.put("v", API_VERSION);
        params.put("sign_method", signMethod);
        params.put("format", FORMAT);
        if (businessParams != null) {
            businessParams.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    params.put(key.trim(), value);
                }
            });
        }
        // 签名覆盖公共参数 + 业务参数（除 sign 自身外全部按字典序拼 k+v），secret 只参与签名、绝不出网
        params.put("sign", PlatformSignatures.topSign(params, appSecret, signMethod));

        URI gateway = gatewayUri(credential);
        log.debug("[AliExpress] TOP 调用 method={} gateway={} sign_method={}", method, gateway.getHost(), signMethod);
        ResponseEntity<String> response = http.postForm(gateway, Map.of(), params, "AliExpress TOP " + method);
        return unwrap(response.getBody(), method);
    }

    /**
     * 生效网关地址：{@code storeUrl} 非空整体覆盖（§5 的海外 host 走这里），否则用 §5.1 默认网关；
     * 覆盖值只填到 host（如 {@code https://eco.taobao.com}）时补上 §5 记录的端点路径 {@code /router/rest}，
     * 填了完整路径则原样使用。
     */
    public URI gatewayUri(PlatformCredential credential) {
        String storeUrl = credential == null ? null : credential.storeUrl();
        String raw = storeUrl == null || storeUrl.isBlank() ? DEFAULT_GATEWAY : storeUrl.trim();
        String url = raw;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("AliExpress TOP 网关地址(storeUrl)无法解析: " + raw, "PLATFORM_STORE_URL_INVALID");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()
                || (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme()))) {
            throw new BusinessException("AliExpress TOP 网关地址(storeUrl)要填完整绝对地址，如 "
                    + DEFAULT_GATEWAY + " 或海外 https://eco.taobao.com/router/rest；留空则用默认网关: " + raw,
                    "PLATFORM_STORE_URL_INVALID");
        }
        String path = parsed.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return URI.create(url + "/router/rest");
        }
        return URI.create(url);
    }

    /**
     * 解 §5.1 的信封：先看 {@code error_response}（失败），命中即抛带 code/msg/sub_code/sub_msg 的
     * {@code PLATFORM_TOP_ERROR}；否则取以方法名派生的成功键 {@code {method_snake}_response} 的内层对象。
     * 派生规则（小写并把 {@code .}/{@code -} 换成 {@code _} 再拼 {@code _response}）是 §5.1 给出的信封字面形态
     * （{@code aliexpress.trade.seller.orderlist.get} → {@code aliexpress_trade_seller_orderlist_get_response}），
     * 不是凭猜。解不到成功键时返回空对象，交由上层按「本窗无数据」处理而不是断言某种形状。
     */
    static Map<String, Object> unwrap(String jsonBody, String method) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Map.of();
        }
        Map<String, Object> root = PlatformJson.object(jsonBody);
        if (root.get("error_response") instanceof Map<?, ?> errorNode && !errorNode.isEmpty()) {
            throw new BusinessException("AliExpress TOP " + method + " 返回错误: " + describeError(asMap(errorNode)),
                    "PLATFORM_TOP_ERROR");
        }
        Object envelope = root.get(responseKey(method));
        if (envelope instanceof Map<?, ?> map) {
            return asMap(map);
        }
        return Map.of();
    }

    /**
     * 由 method 派生成功信封键（§5.1 文档形态）。
     */
    static String responseKey(String method) {
        return method.trim().toLowerCase(Locale.ROOT).replace('.', '_').replace('-', '_') + "_response";
    }

    /**
     * §5.1 失败信封的回显串：只含平台给的错误码/描述，绝不含我方凭据。
     */
    private static String describeError(Map<String, Object> error) {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "code", error.get("code"));
        appendField(builder, "msg", error.get("msg"));
        appendField(builder, "sub_code", error.get("sub_code"));
        appendField(builder, "sub_msg", error.get("sub_msg"));
        return builder.isEmpty() ? "error_response" : builder.toString();
    }

    private static void appendField(StringBuilder builder, String name, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(name).append('=').append(String.valueOf(value).trim());
    }

    /**
     * app_key（{@code apiKey}）：TOP 公共参数，缺失即无法签名。
     */
    public String requireAppKey(PlatformCredential credential) {
        return requireNonBlank(credential == null ? null : credential.apiKey(), "apiKey(app_key)",
                "open.aliexpress.com 入驻 ISV 拿到的 app_key", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    /**
     * app_secret（{@code apiSecret}）：签名密钥，缺失即无法签名（值本身绝不出网、不回显）。
     */
    public String requireAppSecret(PlatformCredential credential) {
        return requireNonBlank(credential == null ? null : credential.apiSecret(), "apiSecret(app_secret)",
                "用于 TOP 请求签名，缺失则无法生成 sign", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    /**
     * session（{@code accessToken}）：卖家授权令牌。速卖通<b>不是</b> OAuth 自动刷新协议，此值由操作员手工粘贴，
     * 缺失意味着还没有一次卖家授权——必须点名 {@code accessToken} 而不是静默降级。
     */
    public String requireSession(PlatformCredential credential) {
        return requireNonBlank(credential == null ? null : credential.accessToken(), "accessToken(session)",
                "TOP 调用需卖家授权后的 access_token（由操作员粘贴进 accessToken 字段，作为 session 公共参数携带）",
                "PLATFORM_SESSION_REQUIRED");
    }

    /**
     * 防失控的翻页上限（供订单适配器循环用），与 {@code PlatformFetcherRegistry} 无关。
     */
    public int maxPages() {
        return maxPages;
    }

    private static String requireNonBlank(String value, String field, String hint, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("AliExpress TOP 缺少 " + field + "：" + hint, code);
        }
        return value.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
