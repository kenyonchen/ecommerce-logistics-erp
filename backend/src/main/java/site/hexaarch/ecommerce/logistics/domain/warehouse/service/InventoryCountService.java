package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;

/**
 * 库存盘点服务，负责执行库存盘点，调整库存数量。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class InventoryCountService {
    private final InventoryRecordRepository inventoryRecordRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * 执行库存盘点。
     *
     * @param warehouseId    仓库ID
     * @param productId      产品ID
     * @param actualQuantity 实际数量
     * @return 盘点后的库存记录
     */
    public InventoryRecord countInventory(String warehouseId, String productId, int actualQuantity) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 查找库存记录
        InventoryRecord inventoryRecord = inventoryRecordRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory record not found: " + productId));

        // 执行库存盘点
        warehouse.countInventory(productId, actualQuantity);

        // 调整库存数量
        inventoryRecord.adjustQuantity(actualQuantity);

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
    }
}