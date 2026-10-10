package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;

import java.util.Optional;

/**
 * 采购单JPA仓库接口，用于采购单的数据库操作。
 *
 * @author kenyon
 */
@Repository
public interface PurchaseOrderJpaRepository extends
        JpaRepository<PurchaseOrderJpaEntity, String>,
        JpaSpecificationExecutor<PurchaseOrderJpaEntity> {

    /**
     * 在指定租户内按采购单号查找（跨租户读取查不到，见 docs/qa/known-issues.md ISS-050）。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实体
     */
    Optional<PurchaseOrderJpaEntity> findByTenantIdAndPurchaseOrderId(String tenantId, String purchaseOrderId);

    /**
     * 统计租户下的采购单总数（首页概览用）
     */
    long countByTenantId(String tenantId);

    /**
     * 统计指向某张仓库的采购单数量（删仓库的引用守卫，ISS-142）。
     * <p>
     * 刻意不按租户过滤：口径与 {@code PurchaseOrderRepository#countByWarehouseId} 一致，
     * 少算一半就等于放过一次把引用掏空的删除。
     * </p>
     *
     * @param warehouseId 仓库ID
     * @return 指向该仓库的采购单数量
     */
    long countByWarehouseId(String warehouseId);
}
