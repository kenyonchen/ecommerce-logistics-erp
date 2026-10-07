package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shopify 发货回写：先 {@code GET orders/{id}.json} 取可履行行项目，再
 * {@code POST orders/{id}/fulfillments.json} 带 {@code tracking_info}。
 * <p>
 * 两步是协议要求而不是冗余：Shopify 的发货体必须显式列出 {@code line_items_by_id}，
 * 订单号本身不足以描述「发了哪些行」。ERP 侧只交给端口订单号 + 运单要素，行项目由这里自查补齐。
 * <p>
 * 幂等靠 {@code fulfillable_quantity} 把关：全部行项目已履行时直接失败返回，避免重复回写生成第二张发货单。
 * <p>
 * 注意 Shopify 已把 Admin REST 冻结、新能力优先落在 GraphQL，{@code fulfillments} 属 legacy 但仍可用；
 * 若后续接口版本移除该端点，需要新增一个 GraphQL 适配器并换注册键，而不是在此处打补丁。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopifyFulfillmentPusher implements PlatformFulfillmentPusher {

    private final ShopifyAdminApi api;

    public ShopifyFulfillmentPusher(ShopifyAdminApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPIFY.name();
    }

    @Override
    public PlatformFulfillmentResult push(PlatformFulfillmentRequest request, PlatformCredential credential) {
        if (request == null || request.trackingNumber() == null || request.trackingNumber().isBlank()) {
            throw new PlatformFulfillmentException("发货回写缺少运单号(trackingNumber)");
        }
        String orderId = requireNumericOrderId(request.platformOrderId());

        Map<String, Object> order = ShopifyJson.wrappedObject(
                api.get(api.resourceUri(credential, "orders/" + orderId + ".json"), credential).getBody(), "order");
        List<String> lineItemIds = fulfillableLineItemIds(order);
        if (lineItemIds.isEmpty()) {
            throw new PlatformFulfillmentException("Shopify 订单 " + orderId
                    + " 没有可履行的行项目（fulfillable_quantity 均为 0），可能已经回写过发货");
        }

        Map<String, Object> fulfillment = ShopifyJson.linkedRow();
        fulfillment.put("line_items_by_id", lineItemIds);
        fulfillment.put("tracking_info", List.of(trackingInfo(request)));
        fulfillment.put("notify_customer", request.notifyCustomer());
        Map<String, Object> body = ShopifyJson.linkedRow();
        body.put("fulfillment", fulfillment);

        ResponseEntity<String> created = api.postJson(
                api.resourceUri(credential, "orders/" + orderId + "/fulfillments.json"), credential, body);
        Map<String, Object> result = ShopifyJson.wrappedObject(created.getBody(), "fulfillment");
        PlatformFulfillmentResult pushed = new PlatformFulfillmentResult(
                ShopifyJson.idText(result, "id"),
                ShopifyJson.text(result, "status"),
                lineItemIds.size(),
                ShopifyJson.toUtcLocalDateTime(ShopifyJson.text(result, "created_at")));
        log.info("[Shopify] 发货回写成功 order={} fulfillment={} status={} lines={}", orderId,
                pushed.platformFulfillmentId(), pushed.status(), pushed.lineItemCount());
        return pushed;
    }

    private Map<String, Object> trackingInfo(PlatformFulfillmentRequest request) {
        Map<String, Object> info = ShopifyJson.linkedRow();
        info.put("number", request.trackingNumber().trim());
        if (request.carrier() != null && !request.carrier().isBlank()) {
            info.put("company", request.carrier().trim());
        }
        if (request.trackingUrl() != null && !request.trackingUrl().isBlank()) {
            info.put("url", request.trackingUrl().trim());
        }
        return info;
    }

    /**
     * 可履行行项目。缺 {@code fulfillable_quantity} 字段时按「可履行」处理，避免旧版本接口整单被跳过。
     */
    static List<String> fulfillableLineItemIds(Map<String, Object> order) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> lineItem : ShopifyJson.objects(ShopifyJson.path(order, "line_items"))) {
            Integer fulfillable = ShopifyJson.integer(lineItem, "fulfillable_quantity");
            if (fulfillable != null && fulfillable <= 0) {
                continue;
            }
            String id = ShopifyJson.idText(lineItem, "id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 订单号要拼进请求路径，因此只接受数字——前台订单号形如 {@code #1001} 时剥掉前缀，
     * 其余任何非数字字符（含 {@code /}、{@code ?}）一律拒绝，防止把路径或查询拼进对方 URL。
     */
    static String requireNumericOrderId(String platformOrderId) {
        String text = platformOrderId == null ? "" : platformOrderId.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            throw new BusinessException("Shopify 发货回写需要平台侧的数字订单 id，当前值: " + platformOrderId,
                    "SHOPIFY_ORDER_ID_INVALID");
        }
        return text;
    }
}
