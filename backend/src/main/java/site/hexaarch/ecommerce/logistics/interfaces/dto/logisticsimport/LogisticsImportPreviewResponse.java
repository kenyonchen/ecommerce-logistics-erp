package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;

/**
 * 物流导入预览结果：包含平台原始字段、生效的字段映射、映射后的物流行与行级错误。
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流导入预览结果")
public class LogisticsImportPreviewResponse {
    private String tenantId;
    private String platform;
    private String integrationId;
    private String fileName;
    @Schema(description = "平台/文件中的原始字段列表")
    private List<String> sourceFields;
    @Schema(description = "实际生效的字段映射")
    private List<FieldMappingDto> mappings;
    @Schema(description = "映射后的标准物流行")
    private List<LogisticsImportRowDto> rows;
    private Integer totalCount;
    @Schema(description = "行级校验错误（预览阶段即可发现）")
    private List<LogisticsImportErrorDto> rowErrors;
    @Schema(description = "处理过程中的提示信息（如降级为示例数据）")
    private List<String> warnings;
}
