package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * eBay Sell 连通性测试：一次最小代价的只读调用 {@code GET /sell/fulfillment/v1/order?limit=1}。
 * <p>
 * Sell 域没有「取店铺信息」这类零成本端点，所以用最便宜的订单读来一次验证三件事：OAuth 换出的 Bearer
 * 令牌是否有效、{@code storeUrl} 覆盖的 host 是否可达、订单端点本身是否接受请求（§4.1/§4.2）。
 * 只发 {@code limit=1}——§4.2 里 {@code filter}/{@code orderIds} 都是可选参数，最小探测不必带时间窗。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）；回显只含 HTTP 码与取回条数，绝不含令牌明文。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class EbayConnectionTester implements PlatformConnectionTester {

    private final EbaySellApi api;

    public EbayConnectionTester(EbaySellApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.EBAY_SELL.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("limit", "1");

            ResponseEntity<String> response = api.get(api.ordersUri(credential, query), credential);
            List<Map<String, Object>> orders = EbayOrderFetcher.ordersIn(response.getBody());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连通 eBay Sell，探测窗口取回 "
                    + orders.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[eBay] 连通性测试失败 err={}", reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
