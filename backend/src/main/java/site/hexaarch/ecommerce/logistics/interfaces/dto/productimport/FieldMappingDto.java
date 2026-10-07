package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

/**
 * 字段映射关系DTO：「平台原始字段 -> 标准商品字段」
 *
 * @author kenyon
 */
@Data
public class FieldMappingDto {
    private String sourceField;
    private String targetField;
    private String transform;
    private String defaultValue;
    private Boolean enabled;
}
