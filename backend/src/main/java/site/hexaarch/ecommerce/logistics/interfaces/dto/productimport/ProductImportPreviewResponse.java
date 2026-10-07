package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

import java.util.List;

/**
 * 商品导入预览结果：包含平台原始字段、生效的字段映射、映射后的商品行与行级错误。
 *
 * @author kenyon
 */
@Data
public class ProductImportPreviewResponse {
    private String tenantId;
    private String platform;
    private String integrationId;
    private String fileName;
    /**
     * 平台/文件中的原始字段列表
     */
    private List<String> sourceFields;
    /**
     * 实际生效的字段映射
     */
    private List<FieldMappingDto> mappings;
    /**
     * 映射后的标准商品行
     */
    private List<ProductImportRowDto> rows;
    private Integer totalCount;
    /**
     * 行级校验错误（预览阶段即可发现）
     */
    private List<ProductImportErrorDto> rowErrors;
    /**
     * 处理过程中的提示信息（如降级为示例数据）
     */
    private List<String> warnings;
}
