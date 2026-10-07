package site.hexaarch.ecommerce.logistics.domain.order.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.OrderImportJob;

import java.util.Optional;

/**
 * 订单导入任务仓储接口
 *
 * @author kenyon
 */
public interface OrderImportJobRepository {
    OrderImportJob save(OrderImportJob job);

    Optional<OrderImportJob> findById(String jobId);

    /**
     * 分页查询导入任务（按开始时间倒序）
     */
    PageResult<OrderImportJob> findByTenantId(String tenantId, int page, int size);
}
