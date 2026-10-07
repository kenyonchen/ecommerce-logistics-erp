package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 店铺仓库字段映射关系DTO：「平台原始字段 -> 标准店铺仓库字段」
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseFieldMappingDto {
    private String sourceField;
    private String targetField;
    private String transform;
    private String defaultValue;
    private Boolean enabled;
}
