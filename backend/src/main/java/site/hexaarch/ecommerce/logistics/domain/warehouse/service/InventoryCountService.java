package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
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
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        // 查找库存记录（仓库聚合内定位；不受仓库限定的 findByProductId 会盘点到别的仓库的行）
        InventoryRecord inventoryRecord = warehouse.findInventoryRecord(productId)
                .or(() -> warehouse.getInventoryRecords().stream()
                        .filter(record -> productId != null && productId.equals(record.getSku()))
                        .findFirst())
                .orElseThrow(() -> new EntityNotFoundException("库存记录不存在: " + productId));

        // 执行库存盘点（聚合内改的就是上面这个实例，无需再调一次 adjustQuantity）
        warehouse.countInventory(inventoryRecord.getProductId(), actualQuantity);

        // 保存库存记录和仓库
        inventoryRecordRepository.save(inventoryRecord);
        warehouseRepository.save(warehouse);

        return inventoryRecord;
    }
}