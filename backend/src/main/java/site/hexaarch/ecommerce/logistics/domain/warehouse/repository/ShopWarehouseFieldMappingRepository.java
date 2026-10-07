package site.hexaarch.ecommerce.logistics.domain.warehouse.repository;

import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseFieldMapping;

import java.util.List;

/**
 * 店铺仓库导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface ShopWarehouseFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<ShopWarehouseFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<ShopWarehouseFieldMapping> saveAll(List<ShopWarehouseFieldMapping> mappings);
}
