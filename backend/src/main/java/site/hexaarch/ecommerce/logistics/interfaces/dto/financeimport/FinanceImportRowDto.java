package site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标准化后的平台财务流水行DTO（导入预览与导入执行共用）
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入财务流水行")
public class FinanceImportRowDto {
    @Schema(description = "行号（从1开始）")
    private Integer rowNumber;
    @Schema(description = "平台交易流水号（幂等去重键）")
    private String externalTransactionId;
    @Schema(description = "交易类型")
    private String type;
    @Schema(description = "金额")
    private BigDecimal amount;
    @Schema(description = "币种（ISO三字母）")
    private String currency;
    @Schema(description = "关联ID")
    private String referenceId;
    @Schema(description = "关联类型")
    private String referenceType;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "交易状态")
    private String status;
    @Schema(description = "发生时间")
    private LocalDateTime occurredAt;
    @Schema(description = "字段级错误明细")
    private List<FinanceImportErrorDto> errors;
}
