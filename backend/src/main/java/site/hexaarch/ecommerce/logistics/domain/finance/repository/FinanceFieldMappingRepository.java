package site.hexaarch.ecommerce.logistics.domain.finance.repository;

import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceFieldMapping;

import java.util.List;

/**
 * 财务导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface FinanceFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<FinanceFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<FinanceFieldMapping> saveAll(List<FinanceFieldMapping> mappings);
}
