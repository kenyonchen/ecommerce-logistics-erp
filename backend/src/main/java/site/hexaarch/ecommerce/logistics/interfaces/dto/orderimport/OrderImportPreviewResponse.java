package site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;

/**
 * 订单导入预览结果：包含识别出的平台原始字段、生效的映射关系、标准化后的待导入行与行级错误。
 *
 * @author kenyon
 */
@Data
@Schema(description = "订单导入预览结果")
public class OrderImportPreviewResponse {
    @Schema(description = "租户ID")
    private String tenantId;
    @Schema(description = "平台名称")
    private String platform;
    @Schema(description = "平台集成ID")
    private String integrationId;
    @Schema(description = "文件名")
    private String fileName;
    @Schema(description = "平台原始字段")
    private List<String> sourceFields;
    @Schema(description = "生效的字段映射")
    private List<FieldMappingDto> mappings;
    @Schema(description = "标准化后的待导入行")
    private List<OrderImportRowDto> rows;
    @Schema(description = "总行数")
    private Integer totalCount;
    @Schema(description = "行级错误汇总")
    private List<OrderImportErrorDto> rowErrors;
    @Schema(description = "处理提示")
    private List<String> warnings;
}
