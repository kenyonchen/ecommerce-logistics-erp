package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import lombok.extern.slf4j.Slf4j;
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
 * Magento 发货回写：{@code POST /rest/<store>/V1/order/<orderId>/ship}，
 * 体 {@code items[{order_item_id, qty}]}、{@code notify}、{@code tracks[{track_number, title, carrier_code}]}
 * （docs/spec/platform-api-reference.md §9）。
 * <p>
 * §9 点名的形状坑都在这里兜住：端点是<b>单数</b> {@code /V1/order/<orderId>/ship}（订单列表是复数
 * {@code orders}，写混了就 404），{@code notify} 直接映射 ERP 的 {@code notifyCustomer}。
 * §9 另记 {@code POST /V1/shipment/} + {@code entity_id} 那一族只在 adobe-commerce.redoc.ly 可查、
 * 而该域名不在本次允许清单内 —— <b>不采用</b>。发货体也只发 §9 点名的这三个键：
 * 真实 2.4 的 {@code ship} 签名里还有总数量 {@code quantity} 与留言 {@code comment}，§9 未记录，
 * 所以不发 —— 实测确认缺哪个再补常量，而不是现在猜。
 * <p>
 * <b>行项目要自查补齐</b>：ERP 只交给端口「订单号 + 运单要素」，而 §9 的发货体要求逐行的
 * {@code order_item_id} 与 {@code qty}，所以先读一次订单再发（与 {@code ShopifyFulfillmentPusher}、
 * {@code EbayFulfillmentPusher} 同一手法）。读订单走的是 §9 <b>已核实</b>的列表端点
 * {@code GET /rest/<store>/V1/orders} 加一个 {@code condition_type=eq} 的 {@code entity_id} 过滤
 * （{@code eq} 在 §9 的 condition_type 全集里），而<b>不是</b>单资源端点 {@code /V1/orders/<id>}——
 * §9 只核实了列表路径，用它是「只用有出处的端点」这条纪律的取舍。
 * 代价是要在结果里挑出那一单，也顺带保证了「读回来的订单号确实是请求的那一单」。
 * 解析不出行项目时抛 {@code MAGENTO_ORDER_ITEMS_UNRESOLVED} 而不是发一个空 {@code items}：
 * 空 items 在平台上可能被当成「整单发货」或静默成功，两种都比明确失败危险。
 * <p>
 * <b>{@code carrier_code} 与 {@code trackingUrl} 被有意不发</b>：§9 只给了键名，没给取值口径。
 * Magento 的 {@code carrier_code} 必须是该店铺已注册的发货承运商代码（如 {@code flatrate}），
 * 而 ERP 的 {@code carrier} 是承运商<b>名称</b>，拿名称去填代码会被平台判非法参数——所以名称放进
 * 自由文本的 {@code title}，代码留空；{@code tracks} 里也没有 §9 记录的跟踪链接键，与其臆造一个
 * {@code carrier_url_from}/{@code carrier_url_to}，不如什么都不发。实测确认取值口径后再补，
 * 补的是常量而不是形状。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MagentoFulfillmentPusher implements PlatformFulfillmentPusher {

    /**
     * §9 的 {@code condition_type} 全集里的等值匹配，用于按订单号精确取单
     */
    private static final String CONDITION_EQ = "eq";

    private final MagentoRestApi api;

    public MagentoFulfillmentPusher(MagentoRestApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MAGENTO.name();
    }

    @Override
    public PlatformFulfillmentResult push(PlatformFulfillmentRequest request, PlatformCredential credential) {
        if (request == null) {
            throw new BusinessException("Magento 发货回写请求为空", "MAGENTO_FULFILLMENT_REQUEST_INVALID");
        }
        String orderId = requireOrderId(request.platformOrderId());
        String trackingNumber = trimToNull(request.trackingNumber());
        if (trackingNumber == null) {
            throw new BusinessException("Magento 发货回写缺少运单号：§9 的发货体靠 tracks[].track_number 承载运单要素，"
                    + "无运单号就没有可回写的东西", "MAGENTO_TRACKING_NUMBER_REQUIRED");
        }
        MagentoAdminToken token = api.tokenFor(credential);

        List<Map<String, Object>> items = resolveItems(credential, token, orderId);

        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("items", items);
        body.put("notify", request.notifyCustomer());
        body.put("tracks", List.of(track(trackingNumber, trimToNull(request.carrier()))));

        String response = api.postJson(api.shipUri(credential, orderId), token, body).getBody();
        Map<String, Object> echo = responseNode(response);
        PlatformFulfillmentResult pushed = new PlatformFulfillmentResult(
                firstNonBlank(PlatformJson.idText(echo, "entity_id"), PlatformJson.idText(echo, "id"), orderId),
                PlatformJson.text(echo, "status"),
                items.size(),
                // 平台没回时间就是空值而不是编一个；§9 未核实发货响应体，故按候选键探测
                MagentoOrderFetcher.toUtcTimestamp(firstNonBlank(PlatformJson.text(echo, "created_at"),
                        PlatformJson.text(echo, "updated_at"))));
        log.info("[Magento] 发货回写成功 order={} shipment={} status={} lines={} notify={}", orderId,
                pushed.platformFulfillmentId(), pushed.status(), pushed.lineItemCount(), request.notifyCustomer());
        return pushed;
    }

    /**
     * 先按 §9 已核实的列表端点 + {@code entity_id eq} 精确取回订单，再把每行折成
     * {@code {order_item_id, qty}}。行项目的键名 §9 未核实，所以按候选键探测。
     */
    private List<Map<String, Object>> resolveItems(PlatformCredential credential, MagentoAdminToken token,
                                                   String orderId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put(MagentoRestApi.PAGE_SIZE_KEY, "1");
        MagentoRestApi.putFilter(query, 0, 0, MagentoOrderFetcher.FIELD_ENTITY_ID, orderId, CONDITION_EQ);

        String body = api.get(api.ordersUri(credential, query), token).getBody();
        Map<String, Object> order = matchOrder(MagentoOrderFetcher.itemsIn(body), orderId);
        if (order == null) {
            throw new BusinessException("Magento 发货回写找不到订单 " + orderId
                    + "：V1/orders 按 entity_id 等值过滤没有返回该单，无法解析发货体的 items[].order_item_id",
                    "MAGENTO_ORDER_NOT_FOUND");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> line : MagentoOrderFetcher.lineItems(order)) {
            String itemId = firstNonBlank(PlatformJson.idText(line, "item_id"), PlatformJson.idText(line, "id"));
            if (itemId == null) {
                continue;
            }
            Map<String, Object> item = PlatformJson.linkedRow();
            item.put("order_item_id", itemId);
            // qty 探不到就不发这一行的数量：§9 没核实默认口径，宁可让平台按它自己的默认处理也不编一个 1
            Integer quantity = firstNonNull(PlatformJson.integer(line, "qty_ordered"),
                    PlatformJson.integer(line, "quantity_ordered"), PlatformJson.integer(line, "qty"));
            if (quantity != null) {
                item.put("qty", quantity);
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new BusinessException("Magento 订单 " + orderId
                    + " 没有可解析的行项目（items[] 里取不到 item_id），无法满足发货体的 items[].order_item_id",
                    "MAGENTO_ORDER_ITEMS_UNRESOLVED");
        }
        return items;
    }

    /**
     * §9 的 {@code tracks[]} 元素：{@code track_number} 装运单号、{@code title} 装承运商名称（自由文本槽位）。
     * {@code carrier_code} 不发，理由见类注释。
     */
    private static Map<String, Object> track(String trackingNumber, String carrier) {
        Map<String, Object> track = PlatformJson.linkedRow();
        track.put("track_number", trackingNumber);
        if (carrier != null) {
            track.put("title", carrier);
        }
        return track;
    }

    /**
     * 订单号要拼进请求路径并作为 {@code entity_id} 过滤值，所以只接受纯数字（{@code entity_id} 是数字主键）；
     * 前台订单号形如 {@code #000000063} 时剥掉前缀，其余非数字（含 {@code /}、{@code ?}）一律拒绝，
     * 防止把路径或查询拼进对方 URL。
     */
    static String requireOrderId(String platformOrderId) {
        String text = platformOrderId == null ? "" : platformOrderId.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            throw new BusinessException("Magento 发货回写需要平台侧的数字订单主键(entity_id)，当前值: "
                    + platformOrderId, "MAGENTO_ORDER_ID_INVALID");
        }
        return text;
    }

    /**
     * 在列表响应里挑出请求的那一单；平台忽略了 {@code eq} 过滤时不能把别人的单发出去。
     */
    private static Map<String, Object> matchOrder(List<Map<String, Object>> orders, String orderId) {
        for (Map<String, Object> order : orders) {
            String id = firstNonBlank(PlatformJson.idText(order, MagentoOrderFetcher.FIELD_ENTITY_ID),
                    PlatformJson.idText(order, "id"));
            if (orderId.equals(id)) {
                return order;
            }
        }
        return null;
    }

    /**
     * 回显节点：发货接口可能直接返回 shipment 对象，也可能包一层。§9 未核实其结构，
     * 因此包装键探不到时退回裸根节点（同 {@code PlatformJson.wrappedObject} 的容错口径）。
     */
    private static Map<String, Object> responseNode(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Map.of();
        }
        return PlatformJson.wrappedObject(jsonBody, "result");
    }

    private static Integer firstNonNull(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
