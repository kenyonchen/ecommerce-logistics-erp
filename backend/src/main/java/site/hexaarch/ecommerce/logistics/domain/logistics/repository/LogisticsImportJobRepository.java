package site.hexaarch.ecommerce.logistics.domain.logistics.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsImportJob;

import java.util.Optional;

/**
 * 物流单导入任务仓储接口
 *
 * @author kenyon
 */
public interface LogisticsImportJobRepository {
    LogisticsImportJob save(LogisticsImportJob job);

    Optional<LogisticsImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<LogisticsImportJob> findByTenantId(String tenantId, int page, int size);
}
