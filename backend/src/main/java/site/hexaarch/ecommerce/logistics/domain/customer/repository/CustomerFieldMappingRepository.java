package site.hexaarch.ecommerce.logistics.domain.customer.repository;

import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerFieldMapping;

import java.util.List;

/**
 * 客户导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface CustomerFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<CustomerFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<CustomerFieldMapping> saveAll(List<CustomerFieldMapping> mappings);
}
