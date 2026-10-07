package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 店铺仓库导入预览结果：包含平台原始字段、生效的字段映射、映射后的店铺仓库行与行级错误。
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseImportPreviewResponse {
    private String tenantId;
    private String platform;
    private String integrationId;
    private String fileName;
    /**
     * 平台/文件中的原始字段列表
     */
    private java.util.List<String> sourceFields;
    /**
     * 实际生效的字段映射
     */
    private java.util.List<ShopWarehouseFieldMappingDto> mappings;
    /**
     * 映射后的标准店铺仓库行
     */
    private java.util.List<ShopWarehouseImportRowDto> rows;
    private Integer totalCount;
    /**
     * 行级校验错误（预览阶段即可发现）
     */
    private java.util.List<ShopWarehouseImportErrorDto> rowErrors;
    /**
     * 处理过程中的提示信息（如降级为示例数据）
     */
    private java.util.List<String> warnings;
}
