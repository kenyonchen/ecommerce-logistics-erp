package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * eBay Sell 发货回写：{@code POST /sell/fulfillment/v1/order/{orderId}/shipping_fulfillment}。
 * <p>
 * 三步里有两步是被 §4.2 现版 schema 逼出来的，不是冗余：
 * <ol>
 *   <li>现版 {@code ShippingFulfillmentDetails} <b>required {@code lineItems}</b>（元素 {@code {id, quantity}}），
 *       但 ERP 只交给端口「订单号 + 运单号 + 承运商」，不含行项目——所以先用 {@code orderIds=<id>}
 *       （§4.2 五个参数之一，正是精确取单用的）把订单查回来，从 {@code orderLineItems[]} 逐行取
 *       {@code lineItem.id} 与购买数量补齐，与 {@code ShopifyFulfillmentPusher} 自查行项目同一手法；</li>
 *   <li>{@code trackingNumber} 与 {@code shippingCarrierCode} 是成对的 body 字段
 *       （§4.2「If you include one, you must also include the other」），缺一即拒；</li>
 *   <li>状态字段是 {@code readOnly}（§4.2），卖家不能指定，故本类<b>不发</b> status。</li>
 * </ol>
 * <b>{@code notifyCustomer} 被有意忽略</b>：§4 通篇没有为 {@code shipping_fulfillment} 记录任何「通知买家」开关
 * （Shopify 有 {@code notify_customer}、Magento 有 {@code notify}，eBay 没有对应已核实字段）。与其臆造一个
 * 请求体键，不如什么都不发——若平台后续核实出该能力，再补而不破坏现有形状。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class EbayFulfillmentPusher implements PlatformFulfillmentPusher {

    private final EbaySellApi api;

    public EbayFulfillmentPusher(EbaySellApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.EBAY_SELL.name();
    }

    @Override
    public PlatformFulfillmentResult push(PlatformFulfillmentRequest request, PlatformCredential credential) {
        if (request == null) {
            throw new BusinessException("eBay 发货回写请求为空", "EBAY_FULFILLMENT_REQUEST_INVALID");
        }
        String orderId = requireOrderId(request.platformOrderId());
        String trackingNumber = trimToNull(request.trackingNumber());
        String carrier = trimToNull(request.carrier());
        requireTrackingPair(trackingNumber, carrier);

        List<Map<String, Object>> lineItems = resolveLineItems(orderId, credential);

        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("shippingCarrierCode", carrier);
        body.put("trackingNumber", trackingNumber);
        body.put("lineItems", lineItems);
        // 不写 status（readOnly）、不写 notifyCustomer（§4 未核实对应字段）

        ResponseEntity<String> created = api.postJson(api.fulfillmentUri(credential, orderId), credential, body);
        Map<String, Object> result = response(created.getBody());
        PlatformFulfillmentResult pushed = new PlatformFulfillmentResult(
                firstNonBlank(PlatformJson.idText(result, "fulfillmentId"), PlatformJson.idText(result, "id")),
                PlatformJson.text(result, "status"),
                lineItems.size(),
                PlatformJson.toUtcLocalDateTime(EbayOrderFetcher.readTime(result, "createdTime")));
        log.info("[eBay] 发货回写成功 order={} fulfillment={} status={} lines={}", orderId,
                pushed.platformFulfillmentId(), pushed.status(), pushed.lineItemCount());
        return pushed;
    }

    /**
     * 用 {@code orderIds=<id>} 精确取回订单并解析履行行。订单/行项目解析不出来时抛专用错误码，
     * 让调用方区别于「平台拒绝发货」——这是数据没取全，不是请求非法。
     */
    private List<Map<String, Object>> resolveLineItems(String orderId, PlatformCredential credential) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("orderIds", orderId);
        query.put("limit", "1");
        ResponseEntity<String> response = api.get(api.ordersUri(credential, query), credential);

        Map<String, Object> order = matchOrder(EbayOrderFetcher.ordersIn(response.getBody()), orderId);
        if (order == null) {
            throw new BusinessException("eBay 发货回写找不到订单 " + orderId
                    + "：orderIds 精确取单没有返回该单，无法解析 required 的 lineItems", "EBAY_ORDER_NOT_FOUND");
        }

        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (Map<String, Object> element : PlatformJson.objects(PlatformJson.path(order, "orderLineItems"))) {
            // 明细内部结构 §4.2 未核实：lineItem.id 与购买数量都按候选键探测
            String lineId = firstNonBlank(PlatformJson.text(element, "lineItem", "id"),
                    PlatformJson.idText(element, "id"));
            if (lineId == null) {
                continue;
            }
            Integer quantity = firstNonNull(PlatformJson.integer(element, "quantityPurchased"),
                    PlatformJson.integer(element, "purchaseQuantity"),
                    PlatformJson.integer(element, "lineItem", "quantity"),
                    PlatformJson.integer(element, "quantity"));
            Map<String, Object> item = PlatformJson.linkedRow();
            item.put("id", lineId);
            if (quantity != null) {
                item.put("quantity", quantity);
            }
            lineItems.add(item);
        }
        if (lineItems.isEmpty()) {
            throw new BusinessException("eBay 订单 " + orderId
                    + " 没有可解析的行项目(orderLineItems 缺 id)，无法满足 shipping_fulfillment 的 required lineItems",
                    "EBAY_LINE_ITEMS_REQUIRED");
        }
        return lineItems;
    }

    /**
     * 订单号要拼进请求路径，因此只接受非空且无路径/查询分隔符的值，防止把 {@code /}、{@code ?} 拼进对方 URL。
     * eBay 订单号形如 {@code 25-12345-67890}（含短横线，非纯数字），故不能像 Shopify 那样强制纯数字。
     */
    static String requireOrderId(String platformOrderId) {
        String text = platformOrderId == null ? "" : platformOrderId.trim();
        if (text.isEmpty() || text.contains("/") || text.contains("?") || text.contains("#")
                || text.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("eBay 发货回写的订单号不合法(含路径/查询/空白字符)，当前值无法安全拼进请求路径: "
                    + platformOrderId, "EBAY_ORDER_ID_INVALID");
        }
        return text;
    }

    /**
     * §4.2：{@code trackingNumber} 与 {@code shippingCarrierCode} 必须成对出现，缺一即拒（也不接受两者都缺，
     * 因为没有可回写的运单要素）。
     */
    private static void requireTrackingPair(String trackingNumber, String carrier) {
        if (trackingNumber == null || carrier == null) {
            throw new BusinessException("eBay 发货回写要求运单号(trackingNumber)与承运商(shippingCarrierCode)成对提交"
                    + "（§4.2：If you include one, you must also include the other），当前"
                    + (trackingNumber == null ? "缺少运单号" : "") + (carrier == null ? (trackingNumber == null ? "与" : "") + "缺少承运商" : ""),
                    "EBAY_TRACKING_PAIR_REQUIRED");
        }
    }

    private static Map<String, Object> matchOrder(List<Map<String, Object>> orders, String orderId) {
        for (Map<String, Object> order : orders) {
            String id = PlatformJson.idText(order, "orderId");
            if (id == null || id.equals(orderId)) {
                return order;
            }
        }
        return null;
    }

    private static Map<String, Object> response(String body) {
        return body == null || body.isBlank() ? Map.of() : PlatformJson.object(body);
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

    private static Integer firstNonNull(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
