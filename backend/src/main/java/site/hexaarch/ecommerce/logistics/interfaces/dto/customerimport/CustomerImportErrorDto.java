package site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户导入错误明细DTO
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入错误明细")
public class CustomerImportErrorDto {
    @Schema(description = "行号")
    private Integer rowNumber;
    @Schema(description = "平台买家ID")
    private String externalBuyerId;
    @Schema(description = "客户名称")
    private String customerName;
    @Schema(description = "出错字段")
    private String field;
    @Schema(description = "错误描述")
    private String message;
}
