package site.hexaarch.ecommerce.logistics.domain.warehouse.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouseImportJob;

import java.util.Optional;

/**
 * 店铺仓库导入任务仓储接口
 *
 * @author kenyon
 */
public interface ShopWarehouseImportJobRepository {
    ShopWarehouseImportJob save(ShopWarehouseImportJob job);

    Optional<ShopWarehouseImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<ShopWarehouseImportJob> findByTenantId(String tenantId, int page, int size);
}
