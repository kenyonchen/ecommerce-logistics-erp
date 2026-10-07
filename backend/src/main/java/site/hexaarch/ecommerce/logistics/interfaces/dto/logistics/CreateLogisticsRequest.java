package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

/**
 * 创建物流单请求。
 * <p>
 * 订单可通过 {@code orderId} 或 {@code orderNumber} 指定；
 * 渠道可通过 {@code logisticsChannelId} 或 {@code channelCode} 指定。
 * </p>
 *
 * @author kenyon
 */
@Data
public class CreateLogisticsRequest {
    private String tenantId;
    private String orderId;
    private String orderNumber;
    private String logisticsChannelId;
    private String channelCode;
    private String trackingNumber;
    private String carrier;
    /**
     * 创建后是否立即推进为「已揽收发货」，用于批量补录已发货数据
     */
    private Boolean shipImmediately;
}
