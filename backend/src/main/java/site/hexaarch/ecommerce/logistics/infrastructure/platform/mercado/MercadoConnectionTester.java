package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mercado Libre 连通性测试：一次最小代价的认证只读调用
 * {@code GET /marketplace/orders/search?seller.id=<user_id>&limit=1}（docs/spec/platform-api-reference.md §7）。
 * <p>
 * §7 没有「取店铺信息」这类零成本端点（商品侧的 {@code /users/{user_id}/items/search} 反而带着没核实的
 * {@code limit} 上限），所以用最便宜的订单读一次同时验证四件事：{@code apiKey}/{@code apiSecret}/
 * {@code refreshToken} 换出的 Bearer 令牌是否被接受、{@code storeUrl} 覆盖的站点 host 是否可达、
 * {@code externalShopId}(卖家 user_id) 是否存在、订单端点本身是否响应。
 * 只带 {@code seller.id} 与 {@code limit=1}、不带时间界，是为了让这一次调用尽量轻——时间界是
 * {@link MercadoOrderFetcher} 的取数条件，不是连通性的必要条件。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）。回显可以含 {@code seller.id}
 * （§2.1 归为非敏感标识），但绝不含访问令牌/刷新令牌/{@code client_secret}（BR-01/BR-02）。
 * 另外按 §7 实测到的错误信封 {@code {message,error,status,cause}} 做一次结构化补全：
 * 传输层的异常消息里带的是原始响应体，本类把它解出来并点明 {@code error=invalid_client} 指向
 * {@code apiKey}/{@code apiSecret} 这一对客户端凭据，运维才能一眼分清「钥匙配错了」和「这个卖家 id 平台不认」。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MercadoConnectionTester implements PlatformConnectionTester {

    /**
     * §7 错误信封里可用于排障的字段；除 {@code cause} 外都是标量
     */
    private static final String[] ENVELOPE_KEYS = {"message", "error", "status"};

    private final MercadoApi api;

    public MercadoConnectionTester(MercadoApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MERCADO_LIBRE.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        String sellerId = null;
        try {
            sellerId = api.sellerId(credential);
            Map<String, String> query = new LinkedHashMap<>();
            query.put(MercadoApi.SELLER_ID_PARAM, sellerId);
            query.put(MercadoApi.LIMIT_PARAM, "1");

            ResponseEntity<String> response = api.get(api.ordersUri(credential, query), credential);
            List<Map<String, Object>> orders = MercadoOrderFetcher.resultsIn(response.getBody());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连通 Mercado Libre "
                    + api.host(credential) + " seller.id=" + sellerId
                    + "，探测窗口取回 " + orders.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = diagnose(e);
            log.warn("[Mercado] 连通性测试失败 seller.id={} err={}", sellerId, reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    /**
     * 把 §7 的实测错误信封摊开：{@code error=invalid_client} 就是客户端凭据（{@code apiKey}/{@code apiSecret}）
     * 这对值没通过，而不是卖家 id 或端点的问题；带 {@code cause} 时一并回显，那里通常写着具体是哪个参数不合法。
     * 响应体不是该信封（或被传输层按 300 字节截断而解析失败）时原样回显异常消息，不做二次加工。
     */
    private static String diagnose(Exception failure) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        Map<String, Object> envelope;
        try {
            envelope = envelopeOf(reason);
        } catch (BusinessException notAnEnvelope) {
            return reason;
        }
        if (envelope.isEmpty()) {
            return reason;
        }
        StringBuilder text = new StringBuilder(reason).append("；平台错误信封");
        for (String key : ENVELOPE_KEYS) {
            Object value = envelope.get(key);
            if (value != null) {
                text.append(' ').append(key).append('=').append(value);
            }
        }
        Object cause = envelope.get("cause");
        if (cause instanceof List<?> list && !list.isEmpty()) {
            String detail = String.valueOf(cause);
            text.append(" cause=").append(detail.length() <= 200 ? detail : detail.substring(0, 200) + "...");
        }
        if ("invalid_client".equals(PlatformJson.text(envelope, "error"))) {
            text.append(" —— §7 实测该错误码对应 client_id/client_secret 校验失败，请核对集成的 apiKey/apiSecret");
        }
        return text.toString();
    }

    /**
     * 从异常消息里截出响应体：{@code PlatformHttpExchange} 的文案是「…: HTTP 400 {body}」形态，
     * 体本身是唯一的一对大括号。这里只取原文，不改写任何凭据（消息里也不会有——头不出现在异常里）。
     */
    private static Map<String, Object> envelopeOf(String message) {
        int start = message.indexOf('{');
        int end = message.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Map.of();
        }
        return PlatformJson.object(message.substring(start, end + 1));
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
