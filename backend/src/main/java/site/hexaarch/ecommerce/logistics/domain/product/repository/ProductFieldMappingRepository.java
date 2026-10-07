package site.hexaarch.ecommerce.logistics.domain.product.repository;

import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductFieldMapping;

import java.util.List;

/**
 * 商品导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface ProductFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<ProductFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<ProductFieldMapping> saveAll(List<ProductFieldMapping> mappings);
}
