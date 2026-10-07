package site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 标准化后的平台买家行DTO（导入预览与导入执行共用）
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入客户行")
public class CustomerImportRowDto {
    @Schema(description = "行号（从1开始）")
    private Integer rowNumber;
    @Schema(description = "平台买家ID（幂等去重键）")
    private String externalBuyerId;
    @Schema(description = "客户名称")
    private String customerName;
    @Schema(description = "客户邮箱")
    private String email;
    @Schema(description = "客户电话")
    private String phone;
    @Schema(description = "公司")
    private String company;
    @Schema(description = "完整地址（国家/省州/城市/详细地址拼接）")
    private String fullAddress;
    @Schema(description = "字段级错误明细")
    private List<CustomerImportErrorDto> errors;
}
