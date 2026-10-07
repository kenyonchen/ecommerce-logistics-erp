package site.hexaarch.ecommerce.logistics.domain.finance.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinanceImportJob;

import java.util.Optional;

/**
 * 财务导入任务仓储接口
 *
 * @author kenyon
 */
public interface FinanceImportJobRepository {
    FinanceImportJob save(FinanceImportJob job);

    Optional<FinanceImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<FinanceImportJob> findByTenantId(String tenantId, int page, int size);
}
