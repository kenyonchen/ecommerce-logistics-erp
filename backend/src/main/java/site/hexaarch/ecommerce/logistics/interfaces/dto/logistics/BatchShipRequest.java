package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

import java.util.List;

/**
 * 批量发货请求。
 * <p>
 * 支持两种定位方式：{@code items} 逐单指定，或 {@code orderIds} 简量列表（使用统一渠道）。
 * </p>
 *
 * @author kenyon
 */
@Data
public class BatchShipRequest {
    private String tenantId;
    /**
     * 统一物流渠道ID（items 未单独指定时使用）
     */
    private String logisticsChannelId;
    /**
     * 统一物流渠道编码（items 未单独指定时使用）
     */
    private String channelCode;
    /**
     * 简化入参：仅给出订单ID列表时按统一渠道发货
     */
    private List<String> orderIds;
    /**
     * 逐单发货明细
     */
    private List<BatchShipItemDto> items;
    /**
     * 发货后是否同步把订单状态推进为已发货，默认 true
     */
    private Boolean updateOrderStatus;
}
