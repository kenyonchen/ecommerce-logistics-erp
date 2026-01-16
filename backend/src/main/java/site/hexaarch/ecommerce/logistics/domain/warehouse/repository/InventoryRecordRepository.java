package site.hexaarch.ecommerce.logistics.domain.warehouse.repository;

import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;

import java.util.List;
import java.util.Optional;

/**
 * 库存记录仓储接口，负责持久化库存记录实体和提供库存记录的访问方法。
 *
 * @author kenyon
 */
public interface InventoryRecordRepository {
    /**
     * 保存库存记录。
     *
     * @param inventoryRecord 库存记录实体
     * @return 保存后的库存记录实体
     */
    InventoryRecord save(InventoryRecord inventoryRecord);

    /**
     * 根据ID查找库存记录。
     *
     * @param id 库存记录ID
     * @return 库存记录实体，如果不存在则返回Optional.empty()
     */
    Optional<InventoryRecord> findById(String id);

    /**
     * 根据产品ID查找库存记录。
     *
     * @param productId 产品ID
     * @return 库存记录实体，如果不存在则返回Optional.empty()
     */
    Optional<InventoryRecord> findByProductId(String productId);

    /**
     * 根据仓库ID查找库存记录。
     *
     * @param warehouseId 仓库ID
     * @return 库存记录实体列表
     */
    List<InventoryRecord> findByWarehouseId(String warehouseId);

    /**
     * 根据库存状态查找库存记录。
     *
     * @param status 库存状态
     * @return 库存记录实体列表
     */
    List<InventoryRecord> findByInventoryStatus(InventoryStatus status);

    /**
     * 查找所有库存记录。
     *
     * @return 库存记录实体列表
     */
    List<InventoryRecord> findAll();

    /**
     * 删除库存记录。
     *
     * @param id 库存记录ID
     */
    void delete(String id);
}