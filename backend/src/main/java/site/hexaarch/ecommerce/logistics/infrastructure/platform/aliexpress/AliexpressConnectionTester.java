package site.hexaarch.ecommerce.logistics.infrastructure.platform.aliexpress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 速卖通连通性测试：一次最小代价的认证只读调用 {@code aliexpress.trade.seller.orderlist.get}，
 * {@code page_size=1}、{@code current_page=1}、时间窗压到 1 天（§5.2 的最便宜订单读）。
 * <p>
 * §5 没有「取店铺信息」这类零成本端点，所以用最便宜的订单读一次同时验证：{@code app_key}/{@code app_secret}
 * 能否算出被网关接受的 {@code sign}、{@code session}（卖家授权令牌）是否有效、{@code storeUrl} 覆盖的网关是否可达、
 * 订单方法是否响应。
 * <p>
 * <b>失败必须区分两类</b>，因为操作员的第一句话永远是「我是不是把钥匙敲错了」：
 * <ul>
 *   <li>(a) 签名/凭据被拒：TOP 把这类错误放在 HTTP 200 的 {@code error_response}（含 {@code sub_code=isv.invalid-*}）里回传，
 *       由 {@link AliexpressTopClient#execute} 归一成 {@code PLATFORM_TOP_ERROR} → 报「凭据或签名被网关拒绝」；</li>
 *   <li>(b) 网关不可达：DNS/连接/超时/HTTP 5xx 一类由 {@code PlatformHttpExchange} 归一成 {@code PLATFORM_REQUEST_FAILED}
 *       → 报「无法访问 TOP 网关」。</li>
 * </ul>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）。回显含网关主机名与条数，
 * 但绝不含 {@code app_secret}/{@code session}/{@code sign}（BR-01/BR-02）；{@code error_response} 里的
 * {@code sub_code}/{@code msg} 是平台给的错误描述、不含我方凭据，可以透出。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AliexpressConnectionTester implements PlatformConnectionTester {

    private static final int PROBE_WINDOW_DAYS = 1;

    private final AliexpressTopClient client;

    public AliexpressConnectionTester(AliexpressTopClient client) {
        this.client = client;
    }

    @Override
    public String type() {
        return PlatformAuthType.ALIEXPRESS.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            LocalDateTime to = LocalDateTime.now();
            LocalDateTime from = to.minusDays(PROBE_WINDOW_DAYS);
            Map<String, Object> query = new LinkedHashMap<>();
            // 1 天窗用 create_date 即可（连通性只验签名与授权是否被接受，不验增量语义）
            query.put("create_date_start", from.format(AliexpressTopClient.TOP_DATE_FORMAT));
            query.put("create_date_end", to.format(AliexpressTopClient.TOP_DATE_FORMAT));
            query.put("current_page", 1);
            query.put("page_size", 1);
            Map<String, String> business = new LinkedHashMap<>();
            business.put("param_aeop_order_query", PlatformJson.write(query));

            Map<String, Object> envelope = client.execute(credential, AliexpressTopClient.ORDER_LIST_METHOD, business);
            int orders = AliexpressOrderFetcher.ordersIn(envelope).size();
            String host = client.gatewayUri(credential).getHost();
            String summary = "已连通速卖通 TOP 网关 " + host + "，1 天探测窗口取回 " + orders + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = classify(e);
            log.warn("[AliExpress] 连通性测试失败 kind={} detail={}", kindOf(e), detail(e));
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    /**
     * 把异常翻译成操作员能据此定位「是钥匙错还是连不上」的话术，只点名字段与错误码，绝不回显凭据值。
     */
    private static String classify(Exception e) {
        String code = e instanceof BusinessException businessException ? businessException.getErrorCode() : null;
        String message = detail(e);
        if ("PLATFORM_TOP_ERROR".equals(code)) {
            // (a) 签名/凭据/授权被拒（error_response，常伴 sub_code=isv.invalid-*）
            return "凭据或签名被 TOP 网关拒绝：请核对 app_key(apiKey)/app_secret(apiSecret) 是否录入正确、"
                    + "以及 session(accessToken) 是否为有效卖家授权。网关详情：" + message;
        }
        if ("PLATFORM_REQUEST_FAILED".equals(code)) {
            // (b) 网关不可达 / HTTP 层失败
            return "无法访问 TOP 网关（网络不通、主机不可达或网关返回 HTTP 错误），"
                    + "请检查 storeUrl 网关地址。网关详情：" + message;
        }
        // 缺字段/地址非法等在我方就能判定的问题
        return message;
    }

    private static String kindOf(Exception e) {
        return e instanceof BusinessException businessException ? businessException.getErrorCode() : e.getClass().getSimpleName();
    }

    private static String detail(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
