package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 物流导入错误明细DTO
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流导入错误明细")
public class LogisticsImportErrorDto {
    @Schema(description = "行号")
    private Integer rowNumber;
    @Schema(description = "平台物流单号")
    private String externalLogisticsId;
    @Schema(description = "系统订单号")
    private String orderNumber;
    @Schema(description = "出错字段")
    private String field;
    @Schema(description = "错误描述")
    private String message;
}
