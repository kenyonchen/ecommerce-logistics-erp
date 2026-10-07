package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

import java.util.List;

/**
 * 保存字段映射请求
 *
 * @author kenyon
 */
@Data
public class FieldMappingSaveRequest {
    private String tenantId;
    private String platform;
    private List<FieldMappingDto> mappings;
}
