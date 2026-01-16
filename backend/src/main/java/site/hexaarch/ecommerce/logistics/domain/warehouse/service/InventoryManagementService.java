package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;

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
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 查找或创建库存记录
        InventoryRecord inventoryRecord = inventoryRecordRepository.findByProductId(productId)
                .orElseGet(() -> createInventoryRecord(warehouseId, productId, sku, locationId));

        // 更新仓库库存
        warehouse.updateInventory(productId, quantity, InventoryMovement.MovementType.INBOUND, "入库");

        // 更新库存记录数量
        inventoryRecord.increaseQuantity(quantity);

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
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 查找库存记录
        InventoryRecord inventoryRecord = inventoryRecordRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory record not found: " + productId));

        // 验证库存数量
        if (inventoryRecord.getQuantity() < quantity) {
            throw new IllegalStateException("Insufficient inventory: " + productId + ", current: " + inventoryRecord.getQuantity() + ", requested: " + quantity);
        }

        // 更新仓库库存
        warehouse.updateInventory(productId, quantity, InventoryMovement.MovementType.OUTBOUND, "出库");

        // 更新库存记录数量
        inventoryRecord.decreaseQuantity(quantity);

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
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 查找库存记录
        InventoryRecord inventoryRecord = inventoryRecordRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory record not found: " + productId));

        // 更新仓库库存
        warehouse.updateInventory(productId, newQuantity, InventoryMovement.MovementType.ADJUSTMENT, "调整");

        // 更新库存记录数量
        inventoryRecord.adjustQuantity(newQuantity);

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
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
                .productId(productId)
                .sku(sku)
                .quantity(0)
                .inventoryStatus(InventoryStatus.OUT_OF_STOCK)
                .locationId(locationId)
                .build();
        return inventoryRecordRepository.save(inventoryRecord);
    }
}