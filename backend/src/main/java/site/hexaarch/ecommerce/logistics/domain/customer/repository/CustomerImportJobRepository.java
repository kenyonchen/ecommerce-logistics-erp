package site.hexaarch.ecommerce.logistics.domain.customer.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.CustomerImportJob;

import java.util.Optional;

/**
 * 客户导入任务仓储接口
 *
 * @author kenyon
 */
public interface CustomerImportJobRepository {
    CustomerImportJob save(CustomerImportJob job);

    Optional<CustomerImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<CustomerImportJob> findByTenantId(String tenantId, int page, int size);
}
