package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;

import java.util.Optional;

/**
 * 库存管理服务，负责管理库存记录，如入库、出库、调拨。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class InventoryManagementService {
    private final InventoryRecordRepository inventoryRecordRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * 库存入库。
     *
     * @param warehouseId 仓库ID
     * @param productId   产品ID
     * @param sku         SKU
     * @param quantity    数量
     * @param locationId  库位ID
     * @return 入库后的库存记录
     */
    public InventoryRecord inboundInventory(String warehouseId, String productId, String sku, int quantity, String locationId) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        InventoryRecord inventoryRecord = findOrCreateRecord(warehouse, productId, sku, locationId);

        // 更新仓库库存
        warehouse.updateInventory(inventoryRecord.getProductId(), quantity, InventoryMovement.MovementType.INBOUND, "入库");

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
    }

    /**
     * 库存出库。
     *
     * @param warehouseId 仓库ID
     * @param productId   产品ID
     * @param quantity    数量
     * @return 出库后的库存记录
     */
    public InventoryRecord outboundInventory(String warehouseId, String productId, int quantity) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        // 查找库存记录
        InventoryRecord inventoryRecord = findRecord(warehouse, productId, productId)
                .orElseThrow(() -> new EntityNotFoundException("库存记录不存在: " + productId));

        // 验证库存数量
        if (inventoryRecord.getQuantity() < quantity) {
            throw new InvalidOperationException("库存不足: " + productId + ", current: " + inventoryRecord.getQuantity() + ", requested: " + quantity);
        }

        // 更新仓库库存
        warehouse.updateInventory(inventoryRecord.getProductId(), quantity, InventoryMovement.MovementType.OUTBOUND, "出库");

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
    }

    /**
     * 库存调整。
     *
     * @param warehouseId 仓库ID
     * @param productId   产品ID
     * @param newQuantity 新的数量
     * @return 调整后的库存记录
     */
    public InventoryRecord adjustInventory(String warehouseId, String productId, int newQuantity) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        // 查找库存记录
        InventoryRecord inventoryRecord = findOrCreateRecord(warehouse, productId, productId, null);

        // 更新仓库库存
        warehouse.updateInventory(inventoryRecord.getProductId(), newQuantity, InventoryMovement.MovementType.ADJUSTMENT, "调整");

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
    }

    /**
     * 在仓库聚合内定位库存记录：先按产品ID，再按 SKU。
     * <p>
     * 库存行的 warehouse_id 非空，聚合加载时已带出该仓库的全部库存行，因此聚合就是仓库维度的定位入口。
     * 不能再退回不受仓库限定的 {@code findByProductId}：调拨时它会命中源仓库的记录，
     * 使目标仓库的聚合里找不到该产品（原报 "Inventory record not found"），或把数量改到别的仓库上。
     * </p>
     */
    private Optional<InventoryRecord> findRecord(Warehouse warehouse, String productId, String sku) {
        return warehouse.findInventoryRecord(productId)
                .or(() -> sku == null ? Optional.empty() : warehouse.getInventoryRecords().stream()
                        .filter(record -> sku.equals(record.getSku()))
                        .findFirst());
    }

    /**
     * 同 {@link #findRecord}，找不到时新建记录并立即挂到聚合上，
     * 使后续 {@code warehouse.updateInventory} 与返回的是同一个实例（否则数量变更只落在聚合的副本上）。
     */
    private InventoryRecord findOrCreateRecord(Warehouse warehouse, String productId, String sku, String locationId) {
        return findRecord(warehouse, productId, sku).orElseGet(() -> {
            InventoryRecord created = createInventoryRecord(warehouse.getWarehouseId(), productId, sku, locationId);
            warehouse.addInventoryRecord(created);
            return created;
        });
    }

    /**
     * 创建库存记录。
     *
     * @param warehouseId 仓库ID
     * @param productId   产品ID
     * @param sku         SKU
     * @param locationId  库位ID
     * @return 创建的库存记录
     */
    private InventoryRecord createInventoryRecord(String warehouseId, String productId, String sku, String locationId) {
        InventoryRecord inventoryRecord = InventoryRecord.builder()
                .warehouseId(warehouseId)
                // 仅传 SKU 的入口（库存入/出接口用 SKU 兼作产品标识）落库后产品ID为空，聚合内按产品ID就再也找不到这行
                .productId(productId != null && !productId.isBlank() ? productId : sku)
                .sku(sku != null && !sku.isBlank() ? sku : productId)
                .quantity(0)
                .inventoryStatus(InventoryStatus.OUT_OF_STOCK)
                .locationId(locationId)
                .build();
        return inventoryRecordRepository.save(inventoryRecord);
    }
}