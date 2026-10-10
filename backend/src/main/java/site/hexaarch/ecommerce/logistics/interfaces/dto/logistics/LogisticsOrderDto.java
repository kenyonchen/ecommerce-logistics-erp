package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流单对外数据结构，避免把领域聚合（含领域事件）直接暴露给前端。
 *
 * @author kenyon
 */
@Data
public class LogisticsOrderDto {
    private String logisticsOrderId;
    private String logisticsOrderNumber;
    private String tenantId;
    private String orderId;
    private String orderNumber;
    private String logisticsChannelId;
    private String channelCode;
    private String channelName;
    private String carrier;
    private String trackingNumber;
    private String trackingUrl;
    private String platform;
    private String externalLogisticsId;
    private String status;
    private String statusLabel;
    private String labelUrl;
    private String labelFormat;
    private String barcode;
    private BigDecimal baseFee;
    private BigDecimal weightFee;
    private BigDecimal totalFee;
    private String currency;
    private String recipient;
    private String phone;
    private String shippingAddress;
    private String shippedAt;
    private String estimatedArrivalAt;
    private String deliveredAt;
    private List<LogisticsTraceDto> traces = new ArrayList<>();
    private String createdAt;
    private String updatedAt;

    /**
     * 由领域聚合构造基础DTO（订单/渠道冗余信息与时间格式化由应用服务补全）
     */
    public static LogisticsOrderDto from(LogisticsOrder order) {
        LogisticsOrderDto dto = new LogisticsOrderDto();
        dto.setLogisticsOrderId(order.getLogisticsOrderId());
        dto.setLogisticsOrderNumber(order.getLogisticsOrderNumber());
        dto.setTenantId(order.getTenantId());
        dto.setOrderId(order.getOrderId());
        dto.setLogisticsChannelId(order.getLogisticsChannelId());
        dto.setCarrier(order.getCarrier());
        dto.setTrackingNumber(order.getTrackingNumber());
        dto.setTrackingUrl(order.getTrackingUrl());
        dto.setPlatform(order.getPlatform());
        dto.setExternalLogisticsId(order.getExternalLogisticsId());
        if (order.getLogisticsStatus() != null) {
            dto.setStatus(order.getLogisticsStatus().name());
            dto.setStatusLabel(order.getLogisticsStatus().getDescription());
        }
        if (order.getLogisticsLabel() != null) {
            dto.setLabelUrl(order.getLogisticsLabel().getLabelUrl());
            dto.setLabelFormat(order.getLogisticsLabel().getLabelFormat());
            dto.setBarcode(order.getLogisticsLabel().getBarcode());
            if (order.getLogisticsLabel().getRecipientInfo() != null) {
                dto.setRecipient(order.getLogisticsLabel().getRecipientInfo().getName());
                dto.setPhone(order.getLogisticsLabel().getRecipientInfo().getPhone());
                dto.setShippingAddress(order.getLogisticsLabel().getRecipientInfo().getAddress());
            }
        }
        if (order.getLogisticsFee() != null && order.getLogisticsFee().getTotalFee() != null) {
            dto.setBaseFee(order.getLogisticsFee().getBaseFee());
            dto.setWeightFee(order.getLogisticsFee().getWeightFee());
            dto.setTotalFee(order.getLogisticsFee().getTotalFee());
            dto.setCurrency(order.getLogisticsFee().getCurrency());
        }
        for (LogisticsTrace trace : order.getTraces()) {
            dto.getTraces().add(LogisticsTraceDto.from(trace));
        }
        return dto;
    }
}
