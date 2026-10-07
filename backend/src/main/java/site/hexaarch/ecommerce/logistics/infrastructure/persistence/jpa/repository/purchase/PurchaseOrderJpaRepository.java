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
}
