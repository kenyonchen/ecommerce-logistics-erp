package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.InventoryRecordJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.InventoryStatusJpaEntity;

/**
 * 库存记录JPA仓库接口，用于操作库存记录JPA实体。
 *
 * @author kenyon
 */
public interface InventoryRecordJpaRepository extends JpaRepository<InventoryRecordJpaEntity, String> {
    /**
     * 根据SKU和仓库ID查找库存记录
     */
    Optional<InventoryRecordJpaEntity> findBySkuCodeAndWarehouseId(String skuCode, String warehouseId);

    /**
     * 根据仓库ID查找库存记录
     */
    List<InventoryRecordJpaEntity> findByWarehouseId(String warehouseId);

    /**
     * 根据SKU查找库存记录
     */
    List<InventoryRecordJpaEntity> findBySkuCode(String skuCode);

    /**
     * 根据产品ID查找库存记录
     */
    Optional<InventoryRecordJpaEntity> findByProductId(String productId);

    /**
     * 根据库存状态查找库存记录
     */
    List<InventoryRecordJpaEntity> findByStatus(InventoryStatusJpaEntity status);
}
