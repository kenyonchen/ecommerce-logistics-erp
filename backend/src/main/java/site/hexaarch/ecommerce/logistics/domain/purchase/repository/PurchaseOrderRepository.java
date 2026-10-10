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

    /**
     * 统计指向某张仓库的采购单数量（删仓库时的引用守卫，ISS-142）。
     * <p>
     * 这是本接口里<b>唯一</b>不带 {@code tenantId} 的读方法，因为它不是业务列表读路径：
     * 仓库ID是 UUID 且全局唯一，"这张仓被多少张采购单指向"按仓ID统计才是完整答案——多带一层租户过滤
     * 只会让别的租户的断链从守卫眼前漏掉。调用方必须先通过 {@code TenantAccessGuard.requireOwned}
     * 拥有该仓库才能问到这个数字（docs/qa/known-issues.md ISS-142），所以这里不构成越权读面。
     * </p>
     *
     * @param warehouseId 仓库ID
     * @return 指向该仓库的采购单数量
     */
    long countByWarehouseId(String warehouseId);
}
