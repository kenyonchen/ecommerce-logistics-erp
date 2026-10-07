package site.hexaarch.ecommerce.logistics.domain.logistics.repository;

import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsFieldMapping;

import java.util.List;

/**
 * 物流单导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface LogisticsFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<LogisticsFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<LogisticsFieldMapping> saveAll(List<LogisticsFieldMapping> mappings);
}
