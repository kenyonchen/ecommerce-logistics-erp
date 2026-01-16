package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;

import java.util.List;

/**
 * 采购单JPA仓库接口，用于采购单的数据库操作。
 *
 * @author kenyon
 */
@Repository
public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrderJpaEntity, String> {
    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    List<PurchaseOrderJpaEntity> findByStatus(String status);

    /**
     * 根据供应商ID查找采购单。
     *
     * @param supplierId 供应商ID
     * @return 采购单列表
     */
    List<PurchaseOrderJpaEntity> findBySupplierId(String supplierId);

    /**
     * 根据仓库ID查找采购单。
     *
     * @param warehouseId 仓库ID
     * @return 采购单列表
     */
    List<PurchaseOrderJpaEntity> findByWarehouseId(String warehouseId);
}
