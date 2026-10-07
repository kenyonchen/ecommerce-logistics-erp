package site.hexaarch.ecommerce.logistics.domain.product.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.ProductImportJob;

import java.util.Optional;

/**
 * 商品导入任务仓储接口
 *
 * @author kenyon
 */
public interface ProductImportJobRepository {
    ProductImportJob save(ProductImportJob job);

    Optional<ProductImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<ProductImportJob> findByTenantId(String tenantId, int page, int size);
}
