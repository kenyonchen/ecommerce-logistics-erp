package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.time.LocalDateTime;

/**
 * 发货回写端口（outbound）：把本地已发货的运单号回写到平台订单，让平台侧买家能看到物流信息。
 * <p>
 * 与 {@code Platform*Fetcher} 同一套注册键分派。请求里只给平台订单号与运单要素，
 * 平台要求的行项目明细（如 Shopify 的 {@code line_items_by_id}）由适配器自查订单后补齐——
 * 那是协议细节，不该漏进 ERP 的物流聚合。
 *
 * @author kenyon
 */
public interface PlatformFulfillmentPusher {

    /**
     * 适配器注册键（CUSTOM_SHOP / SHOPIFY）
     */
    String type();

    PlatformFulfillmentResult push(PlatformFulfillmentRequest request, PlatformCredential credential);

    /**
     * @param platformOrderId 平台侧订单号（Shopify 为订单数字 id）
     * @param trackingNumber  运单号
     * @param carrier         承运商名称
     * @param trackingUrl     跟踪链接，可空
     * @param notifyCustomer  是否让平台给买家发通知邮件
     */
    record PlatformFulfillmentRequest(String platformOrderId, String trackingNumber,
                                      String carrier, String trackingUrl, boolean notifyCustomer) {
    }

    /**
     * @param platformFulfillmentId 平台侧生成的发货单 id
     * @param status                平台侧发货状态
     * @param lineItemCount         本次回写覆盖的行项目数
     * @param fulfilledAt           平台侧履行时间
     */
    record PlatformFulfillmentResult(String platformFulfillmentId, String status,
                                     int lineItemCount, LocalDateTime fulfilledAt) {
    }
}
