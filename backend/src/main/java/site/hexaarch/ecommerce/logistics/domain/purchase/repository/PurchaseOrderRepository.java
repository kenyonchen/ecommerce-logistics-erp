package site.hexaarch.ecommerce.logistics.domain.purchase.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;

import java.util.Optional;

/**
 * 采购单仓储接口，负责采购单的持久化和查询操作。
 * <p>
 * 所有读方法都带 tenantId：采购单曾经可以跨租户直读（docs/qa/known-issues.md ISS-050），
 * 租户条件放在仓储签名上而不是靠调用方自觉，避免再次出现漏过滤。
 * </p>
 *
 * @author kenyon
 */
public interface PurchaseOrderRepository {
    /**
     * 保存采购单。
     * <p>
     * 刻意返回 void：调用方（领域服务）持有的聚合实例才是事件的唯一来源，
     * 若返回映射副本会清空待发事件队列，见 docs/qa/known-issues.md ISS-062。
     * </p>
     *
     * @param purchaseOrder 采购单实例
     */
    void save(PurchaseOrder purchaseOrder);

    /**
     * 在指定租户内按采购单号查找采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实例，若不存在或不属于该租户则返回Optional.empty()
     */
    Optional<PurchaseOrder> findById(String tenantId, String purchaseOrderId);

    /**
     * 分页 + 条件查询采购单，按创建时间倒序。
     *
     * @param query 查询条件
     * @param page  页码（从0开始）
     * @param size  每页条数
     * @return 分页结果
     */
    PageResult<PurchaseOrder> search(PurchaseQuery query, int page, int size);
}
