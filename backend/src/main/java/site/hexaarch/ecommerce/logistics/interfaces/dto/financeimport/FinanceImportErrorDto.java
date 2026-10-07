package site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 财务导入错误明细DTO
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入错误明细")
public class FinanceImportErrorDto {
    @Schema(description = "行号")
    private Integer rowNumber;
    @Schema(description = "平台交易流水号")
    private String externalTransactionId;
    @Schema(description = "出错字段")
    private String field;
    @Schema(description = "错误描述")
    private String message;
}
