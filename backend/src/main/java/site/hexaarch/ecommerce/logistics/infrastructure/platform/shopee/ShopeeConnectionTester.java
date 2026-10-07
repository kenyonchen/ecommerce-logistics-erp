package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Shopee 连通性测试：一次最小代价的认证只读调用 {@code POST /api/v2/order/get_order_list}，
 * 时间窗压到 1 天、{@code page_size=1}。
 * <p>
 * §6 没有「取店铺信息」这类零成本端点，所以用最便宜的订单读来一次验证四件事：
 * partner_id/partner_key 算出的 {@code sign} 是否被接受、{@code access_token}/{@code shop_id} 是否匹配该店、
 * {@code x-env-type} 选的环境是否可达、订单端点本身是否响应（§6）。窗口取 1 天（≤ §6 的 15 天上限，不会因跨度被拒）。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）。回显含 {@code shop_id}（§2.1 归为非敏感标识），
 * 但绝不含 {@code access_token}/{@code partner_key}/{@code sign}（BR-01/BR-02）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopeeConnectionTester implements PlatformConnectionTester {

    private static final int PROBE_WINDOW_DAYS = 1;

    private final ShopeeOpenApi api;

    public ShopeeConnectionTester(ShopeeOpenApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPEE.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime from = to.minusDays(PROBE_WINDOW_DAYS);
            Map<String, Object> body = PlatformJson.linkedRow();
            body.put("time_range_field", "create_time");
            body.put("time_from", PlatformJson.toEpochSeconds(from));
            body.put("time_to", PlatformJson.toEpochSeconds(to));
            body.put("page_size", 1);

            String responseBody = api.postJson(credential, ShopeeOpenApi.ORDER_LIST_PATH, body).getBody();
            List<Map<String, Object>> orders = ShopeeOrderFetcher.ordersIn(responseBody);
            String summary = "已连通 Shopee Open Platform v2 shop_id="
                    + (credential == null ? null : credential.externalShopId())
                    + "，探测窗口取回 " + orders.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[Shopee] 连通性测试失败 shop_id={} err={}",
                    credential == null ? null : credential.externalShopId(), reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
