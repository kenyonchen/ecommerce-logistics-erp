package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amazon SP-API 连通性测试：一次最小代价的只读调用 {@code GET /orders/v0/orders?MaxResultsPerPage=1}。
 * <p>
 * SP-API 没有「取店铺信息」这类零成本端点，所以用最便宜的订单读来同时验证四件事：
 * LWA 换出的访问令牌是否有效、{@code region} 选出的 host 是否是该卖家注册的区域、
 * {@code marketplaceIds} 是否被该平台接受、以及订单端点本身是否可达（§3.1/§3.2/§3.3）。
 * 单页取 1 条并把时间窗压到 1 天，是为了在 0.0167 rps 的速率下几乎不消耗配额。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）；回显只含区域与市场数量，不含任何令牌。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AmazonConnectionTester implements PlatformConnectionTester {

    private static final int PROBE_WINDOW_DAYS = 1;

    private final AmazonSpApi api;

    public AmazonConnectionTester(AmazonSpApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.AMAZON_SP_API.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("CreatedAfter", PlatformJson.toIsoUtcQueryTimestamp(
                    LocalDateTime.now(ZoneOffset.UTC).minusDays(PROBE_WINDOW_DAYS)));
            String marketplaceIds = AmazonSpApi.marketplaceIdsParam(credential);
            query.put("MarketplaceIds", marketplaceIds);
            query.put("MaxResultsPerPage", "1");

            ResponseEntity<String> response = api.get(api.ordersUri(credential, query), credential);
            List<Map<String, Object>> orders = AmazonOrderFetcher.ordersIn(response.getBody());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连通 Amazon SP-API "
                    + api.regionLabel(credential) + " 区域，市场 " + marketplaceIds.split(",").length
                    + " 个，探测窗口取回 " + orders.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[Amazon] 连通性测试失败 region={} err={}", api.regionLabel(credential), reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
