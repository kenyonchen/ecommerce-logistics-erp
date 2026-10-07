package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

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
 * Magento 连通性测试：一次最小代价的认证只读调用
 * {@code GET /rest/<store>/V1/orders?searchCriteria[pageSize]=1}（docs/spec/platform-api-reference.md §9）。
 * <p>
 * §9 没有「取店铺信息」这类零成本端点，所以用最便宜的订单读一次同时验证四件事：
 * base URL 填对了没有（Magento 是自建部署，{@code storeUrl} 就是 host 的唯一来源）、
 * 令牌这一环能不能成（粘贴的 {@code accessToken} 被接受，或 {@code apiKey}/{@code apiSecret}
 * 换 Bearer 的 {@code PUT /rest/V1/integration/admin/token} 跑得通）、
 * {@code store_view_code}（{@code region}）在这台实例上存不存在、订单端点是否响应。
 * 单页只取 1 条，且不带时间过滤，是为了让这一次调用尽量轻。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）。回显只有 HTTP 码、生效的 base 主机名与 store view：
 * 令牌与管理员口令都是凭据（{@code accessToken}/{@code apiKey}/{@code apiSecret}），
 * 任何一个都不出现在消息里（BR-01/BR-02）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MagentoConnectionTester implements PlatformConnectionTester {

    private final MagentoRestApi api;

    public MagentoConnectionTester(MagentoRestApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MAGENTO.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        String storeView = null;
        try {
            storeView = api.storeView(credential);
            Map<String, String> query = new LinkedHashMap<>();
            query.put(MagentoRestApi.PAGE_SIZE_KEY, "1");
            ResponseEntity<String> response = api.get(api.ordersUri(credential, query), api.tokenFor(credential));
            List<Map<String, Object>> orders = MagentoOrderFetcher.itemsIn(response.getBody());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连通 Magento REST "
                    + api.host(credential) + " 的 store view " + storeView + "，探测窗口取回 " + orders.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[Magento] 连通性测试失败 storeView={} err={}", storeView, reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
