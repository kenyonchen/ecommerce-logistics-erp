package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 保存店铺仓库字段映射请求
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseFieldMappingSaveRequest {
    private String tenantId;
    private String platform;
    private java.util.List<ShopWarehouseFieldMappingDto> mappings;
}
