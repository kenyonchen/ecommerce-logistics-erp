package site.hexaarch.ecommerce.logistics.interfaces.dto.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;

/**
 * 创建财务交易请求体。
 *
 * @author kenyon
 */
@Data
@Schema(description = "创建财务交易请求")
public class CreateFinancialTransactionRequest {
    @Schema(description = "交易类型", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "INCOME", allowableValues = {"INCOME", "EXPENSE", "REFUND", "ADJUSTMENT"})
    private TransactionType type;

    @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "199.00")
    private BigDecimal amount;

    @Schema(description = "币种（ISO三字母）", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNY")
    private String currency;

    @Schema(description = "关联ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORD-001")
    private String referenceId;

    @Schema(description = "关联类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER")
    private String referenceType;

    @Schema(description = "交易描述", example = "平台回款")
    private String description;

    @Schema(description = "租户ID（可选，缺省归属默认租户）", example = "default-tenant")
    private String tenantId;
}
