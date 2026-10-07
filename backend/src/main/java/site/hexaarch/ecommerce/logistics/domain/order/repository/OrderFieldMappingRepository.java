package site.hexaarch.ecommerce.logistics.domain.order.repository;

import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderFieldMapping;

import java.util.List;

/**
 * 订单导入字段映射仓储接口
 *
 * @author kenyon
 */
public interface OrderFieldMappingRepository {
    /**
     * 查询指定租户在某个平台下的字段映射
     */
    List<OrderFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 删除指定租户在某个平台下的全部字段映射
     */
    void deleteByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 批量保存字段映射
     */
    List<OrderFieldMapping> saveAll(List<OrderFieldMapping> mappings);
}
