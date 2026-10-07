package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

/**
 * 批量发货的单条明细。
 *
 * @author kenyon
 */
@Data
public class BatchShipItemDto {
    private Integer rowNumber;
    /**
     * 系统订单ID，与 orderNumber / externalOrderId 三选一
     */
    private String orderId;
    /**
     * 系统订单号
     */
    private String orderNumber;
    /**
     * 第三方平台订单号
     */
    private String externalOrderId;
    /**
     * 物流渠道ID，缺省时使用请求上的统一渠道
     */
    private String logisticsChannelId;
    /**
     * 物流渠道编码，缺省时使用请求上的统一渠道
     */
    private String channelCode;
    /**
     * 运单号，为空时由系统生成
     */
    private String trackingNumber;
    /**
     * 承运商名称
     */
    private String carrier;
    /**
     * 发货时间（yyyy-MM-dd HH:mm:ss），为空时取当前时间
     */
    private String shippedAt;
}
