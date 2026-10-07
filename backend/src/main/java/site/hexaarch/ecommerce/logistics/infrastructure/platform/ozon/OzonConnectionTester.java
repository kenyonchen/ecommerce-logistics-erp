package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

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
import java.util.List;
import java.util.Map;

/**
 * Ozon 连通性测试：一次最小代价的认证只读调用 {@code POST /v3/posting/fbs/list}，{@code filter.limit=1}。
 * <p>
 * §8 没有「取店铺信息」这类零成本端点（唯一非读数的 {@code /v2/warehouse/list} 也是 POST 列表，且店铺仓库
 * 不在本轮交付范围），所以用最便宜的订单读来一次验证三件事：{@code Client-Id}/{@code Api-Key} 是否被接受
 * （缺一即 401）、{@code storeUrl} 覆盖的 host 是否可达、订单端点本身是否响应。
 * 时间窗压到 1 天并把 {@code filter.limit} 取 1，是为了让这一次调用不带出任何明细回取。
 * <p>
 * 失败按结果返回而不是抛异常，让前端能回显原因（BR-04）。回显只有 HTTP 码、host 与条数：
 * Api-Key 是凭据（{@code apiSecret}），Client-Id 同样是凭据（{@code apiKey}），两个都不出现在消息里（BR-01/BR-02）。
 * 另按 §8 提醒：Api-Key 过期时平台返回 500 而不是 401，所以本测试把 500 也当凭据问题回显，
 * 具体原因由 {@code PlatformHttpExchange} 的错误文案给出。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class OzonConnectionTester implements PlatformConnectionTester {

    private static final int PROBE_WINDOW_DAYS = 1;

    private final OzonSellerApi api;

    public OzonConnectionTester(OzonSellerApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.OZON.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            LocalDateTime until = LocalDateTime.now(ZoneOffset.UTC);
            Map<String, Object> filter = PlatformJson.linkedRow();
            // §8 的坑：时间必须带偏移，裸 LocalDateTime 会被判 bad time，连通性测试也会因此假报成功/失败
            filter.put("since", PlatformJson.toOffsetQueryTimestamp(
                    until.minusDays(PROBE_WINDOW_DAYS), ZoneOffset.UTC));
            filter.put("until", PlatformJson.toOffsetQueryTimestamp(until, ZoneOffset.UTC));
            filter.put("limit", 1);
            filter.put("offset", 0);
            Map<String, Object> body = PlatformJson.linkedRow();
            body.put("dir", OzonOrderFetcher.DIRECTION);
            body.put("sort", OzonOrderFetcher.SORT_FIELD);
            body.put("filter", filter);
            body.put("with", PlatformJson.linkedRow());

            ResponseEntity<String> response = api.postJson(credential, OzonSellerApi.ORDERS_LIST_PATH, body);
            List<Map<String, Object>> posts = OzonOrderFetcher.postsIn(response.getBody());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连通 Ozon Seller API "
                    + api.host(credential) + "，探测窗口取回 " + posts.size() + " 条订单";
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[Ozon] 连通性测试失败 err={}", reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
