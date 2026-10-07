package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

/**
 * 标准目标字段元数据，供前端渲染字段映射下拉框
 *
 * @author kenyon
 */
@Data
public class TargetFieldDto {
    private String code;
    private String label;
    private Boolean required;

    public TargetFieldDto(String code, String label, Boolean required) {
        this.code = code;
        this.label = label;
        this.required = required;
    }
}
