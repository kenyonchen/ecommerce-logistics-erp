package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 经过字段映射后的标准化物流行，是第三方平台物流数据进入领域模型之前的中间结构。
 *
 * @author kenyon
 */
public record ImportedLogisticsRow(
        int rowNumber,
        String externalLogisticsId,
        String trackingNumber,
        String orderNumber,
        String externalOrderId,
        String channelCode,
        String carrier,
        LogisticsStatus status,
        String trackingUrl,
        String labelUrl,
        String labelFormat,
        String barcode,
        LocalDateTime shippedAt,
        LocalDateTime estimatedArrivalAt,
        LocalDateTime deliveredAt,
        BigDecimal totalFee,
        String currency,
        String recipient,
        String phone,
        String country,
        String province,
        String city,
        String street,
        String zipCode,
        List<LogisticsTrace> traces
) {
}
