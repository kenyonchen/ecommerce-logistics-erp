package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.util.List;
import java.util.Map;

/**
 * Ozon 发货回写：{@code POST /v2/posts/add}，体 {@code {status, tracking_number, posts:[{posting_number, tracking_status}]}}。
 * <p>
 * §8 把这一家列为「实现前必须核实、否则直接写错」的位置：端点是 <b>{@code /v2/posts/add}，不是 {@code /v4/…} 开头
 * 的那一族</b>，字段是 <b>{@code tracking_number}，不是拼写极像的 {@code tracing_number}</b>。
 * 两处都在契约测试里按字面值断言，因为写错一个字母平台就拒单，而本地 fixture 看不出差别。
 * <p>
 * 一次回写只覆盖一张贴子的运单（{@code posts} 数组按 §8 的形状发，长度 1），因此 {@code lineItemCount} 恒为 1。
 * <p>
 * <b>{@code carrier}/{@code trackingUrl}/{@code notifyCustomer} 被有意忽略</b>：§8 记录的请求体里
 * 没有承运商、跟踪链接、通知买家这三类字段（Shopify 有 {@code tracking_info.company/url}、eBay 有
 * {@code shippingCarrierCode}，Ozon 都没有对应已核实字段）。与其臆造键名，不如什么都不发。
 * {@code posts[].tracking_status} 的取值集合 §8 也没核实，这里与外层 {@code status} 用同一字面值，
 * 实测后需要改的只有这一个常量。
 * 响应体结构未被 §8 核实，故回写结果按候选键探测，探不到就只带回本次发出去的贴子号。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class OzonFulfillmentPusher implements PlatformFulfillmentPusher {

    /**
     * §8 给出的状态字面值；外层 {@code status} 与 {@code posts[].tracking_status} 共用
     */
    static final String AWAITING_DELIVERY = "awaiting_delivery";

    private final OzonSellerApi api;

    public OzonFulfillmentPusher(OzonSellerApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.OZON.name();
    }

    @Override
    public PlatformFulfillmentResult push(PlatformFulfillmentRequest request, PlatformCredential credential) {
        if (request == null) {
            throw new BusinessException("Ozon 发货回写请求为空", "OZON_FULFILLMENT_REQUEST_INVALID");
        }
        String postingNumber = requirePostingNumber(request.platformOrderId());
        String trackingNumber = trimToNull(request.trackingNumber());
        if (trackingNumber == null) {
            throw new BusinessException("Ozon 发货回写缺少运单号：§8 的字段名是 tracking_number"
                    + "（不是 tracing_number），无运单号就没有可回写的要素", "OZON_TRACKING_NUMBER_REQUIRED");
        }

        Map<String, Object> post = PlatformJson.linkedRow();
        post.put("posting_number", postingNumber);
        post.put("tracking_status", AWAITING_DELIVERY);
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("status", AWAITING_DELIVERY);
        body.put("tracking_number", trackingNumber);
        body.put("posts", List.of(post));

        String response = api.postJson(credential, OzonSellerApi.FULFILLMENT_PATH, body).getBody();
        Map<String, Object> echo = resultNode(response);
        PlatformFulfillmentResult pushed = new PlatformFulfillmentResult(
                firstNonBlank(PlatformJson.text(echo, "posting_number"), postingNumber),
                PlatformJson.text(echo, "status"),
                1,
                // toUtcLocalDateTime 对 null/空白直接返回 null，平台没回时间就是空值而不是编一个
                PlatformJson.toUtcLocalDateTime(firstNonBlank(PlatformJson.text(echo, "in_process_at"),
                        PlatformJson.text(echo, "created_at"))));
        log.info("[Ozon] 发货回写成功 posting={} tracking_set=true status={}",
                postingNumber, pushed.status() == null ? "-" : pushed.status());
        return pushed;
    }

    /**
     * 贴子号要作为 §8 的 {@code posts[].posting_number} 发出去，形态形如 {@code 0998957729-0001}；
     * 这里不拼 URL 路径，但仍拒绝空白与含路径/查询分隔符的值，避免把脏值当单号发给平台。
     */
    static String requirePostingNumber(String platformOrderId) {
        String text = platformOrderId == null ? "" : platformOrderId.trim();
        if (text.isEmpty() || text.contains("/") || text.contains("?") || text.contains("#")
                || text.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("Ozon 发货回写的贴子号(posting_number)不合法(为空或含路径/查询/空白字符): "
                    + platformOrderId, "OZON_POSTING_NUMBER_INVALID");
        }
        return text;
    }

    /**
     * 回显节点：{@code result} 是数组时取第一个元素，是对象时直接用它，都没有就用裸根节点。
     */
    private static Map<String, Object> resultNode(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Map.of();
        }
        Map<String, Object> root = PlatformJson.object(jsonBody);
        Object result = root.get("result");
        if (result instanceof List<?> list) {
            List<Map<String, Object>> rows = PlatformJson.objects(list);
            return rows.isEmpty() ? root : rows.get(0);
        }
        if (result instanceof Map<?, ?>) {
            return PlatformJson.nested(root, "result");
        }
        return root;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
