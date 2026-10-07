package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

/**
 * 批量发货的单条失败/跳过明细。
 *
 * @author kenyon
 */
@Data
public class BatchShipFailureDto {
    private Integer rowNumber;
    private String orderNumber;
    private String type;
    private String reason;

    public static BatchShipFailureDto failed(Integer rowNumber, String orderNumber, String reason) {
        BatchShipFailureDto dto = new BatchShipFailureDto();
        dto.setRowNumber(rowNumber);
        dto.setOrderNumber(orderNumber);
        dto.setType("FAILURE");
        dto.setReason(reason);
        return dto;
    }

    public static BatchShipFailureDto skipped(String orderNumber, String reason) {
        BatchShipFailureDto dto = new BatchShipFailureDto();
        dto.setOrderNumber(orderNumber);
        dto.setType("SKIPPED");
        dto.setReason(reason);
        return dto;
    }
}
