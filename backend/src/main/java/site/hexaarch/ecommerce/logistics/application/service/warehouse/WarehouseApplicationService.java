package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.Location;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryCountService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.WarehouseManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓储应用服务，协调领域对象完成仓储相关的业务操作。
 *
 * @author kenyon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseApplicationService {
    private final WarehouseRepository warehouseRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final WarehouseManagementService warehouseManagementService;
    private final InventoryManagementService inventoryManagementService;
    private final InventoryCountService inventoryCountService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 创建仓库
     */
    @Transactional
    public Warehouse createWarehouse(String tenantId, String warehouseName, String warehouseCode, String address, int capacity) {
        var warehouse = warehouseManagementService.createWarehouse(tenantId, warehouseName, warehouseCode, address, capacity);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 更新仓库
     */
    @Transactional
    public Warehouse updateWarehouse(String warehouseId, String warehouseName, String warehouseCode, String address, int capacity) {
        var warehouse = warehouseManagementService.updateWarehouse(warehouseId, warehouseName, warehouseCode, address, capacity);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 删除仓库
     */
    @Transactional
    public void deleteWarehouse(String warehouseId) {
        Warehouse warehouse = getWarehouse(warehouseId);
        List<InventoryRecord> inventoryRecords = inventoryRecordRepository.findByWarehouseId(warehouseId);
        boolean hasStock = inventoryRecords.stream().anyMatch(record -> record.getQuantity() > 0);
        if (hasStock) {
            throw new ValidationException("仓库中仍存在库存记录，无法删除，请先清空库存");
        }
        inventoryRecordRepository.findByWarehouseId(warehouseId)
                .forEach(record -> inventoryRecordRepository.delete(record.getInventoryId()));
        warehouseRepository.delete(warehouse.getWarehouseId());
    }

    /**
     * 启用/停用仓库
     */
    @Transactional
    public Warehouse changeWarehouseStatus(String warehouseId, boolean active) {
        Warehouse warehouse = getWarehouse(warehouseId);
        Warehouse updated = warehouse.toBuilder().active(active).build();
        return warehouseRepository.save(updated);
    }

    /**
     * 添加库位
     */
    @Transactional
    public Warehouse addLocation(String warehouseId, String locationCode, String locationType, Integer capacity) {
        if (locationCode == null || locationCode.isBlank()) {
            throw new ValidationException("库位编码不能为空");
        }
        Location location = Location.builder()
                .warehouseId(warehouseId)
                .locationCode(locationCode.trim())
                .locationType(locationType == null || locationType.isBlank() ? "SHELF" : locationType.trim())
                .capacity(capacity == null || capacity <= 0 ? 1 : capacity)
                .occupied(0)
                .active(true)
                .build();
        var warehouse = warehouseManagementService.addLocation(warehouseId, location);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 库存入库
     */
    @Transactional
    public InventoryRecord inventoryIn(String skuCode, String warehouseId, Integer quantity) {
        requireWarehouse(warehouseId);
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("SKU编码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("入库数量必须大于0");
        }
        return inventoryManagementService.inboundInventory(warehouseId, skuCode, skuCode, quantity, null);
    }

    /**
     * 库存出库
     */
    @Transactional
    public InventoryRecord inventoryOut(String skuCode, String warehouseId, Integer quantity) {
        requireWarehouse(warehouseId);
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("SKU编码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("出库数量必须大于0");
        }
        return inventoryManagementService.outboundInventory(warehouseId, skuCode, quantity);
    }

    /**
     * 执行库存盘点
     */
    @Transactional
    public List<InventoryRecord> executeInventoryCount(String warehouseId, List<InventoryMovement> movements) {
        requireWarehouse(warehouseId);
        List<InventoryRecord> results = new ArrayList<>();
        if (movements == null || movements.isEmpty()) {
            return results;
        }
        for (InventoryMovement movement : movements) {
            results.add(inventoryCountService.countInventory(warehouseId, movement.getProductId(), movement.getQuantity()));
        }
        return results;
    }

    /**
     * 查询仓库库存
     */
    public List<InventoryRecord> getWarehouseInventory(String warehouseId) {
        return inventoryRecordRepository.findByWarehouseId(warehouseId);
    }

    /**
     * 根据ID查询仓库
     */
    public Warehouse findWarehouseById(String id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + id));
    }

    /**
     * 查询所有仓库
     */
    public List<Warehouse> findAllWarehouses() {
        return warehouseRepository.findAll();
    }

    /**
     * 根据仓库编码查询仓库
     */
    public Warehouse findWarehouseByCode(String warehouseCode) {
        return warehouseRepository.findByWarehouseCode(warehouseCode)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseCode));
    }

    /**
     * 根据仓库ID查询库存
     */
    public List<InventoryRecord> findInventoryByWarehouse(String warehouseId) {
        return inventoryRecordRepository.findByWarehouseId(warehouseId);
    }

    /**
     * 库存盘点（单个产品）
     */
    @Transactional
    public InventoryRecord countInventory(String warehouseId, String productId, int actualQuantity) {
        return inventoryCountService.countInventory(warehouseId, productId, actualQuantity);
    }

    /**
     * 库存移动
     */
    @Transactional
    public InventoryRecord moveInventory(String warehouseId, InventoryMovement movement) {
        if (movement == null) {
            throw new ValidationException("库存移动参数不能为空");
        }
        requireWarehouse(warehouseId);
        // 根据移动类型执行相应的库存操作
        if (movement.getMovementType() == InventoryMovement.MovementType.INBOUND) {
            return inventoryManagementService.inboundInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getSku(),
                    movement.getQuantity(),
                    null
            );
        } else if (movement.getMovementType() == InventoryMovement.MovementType.OUTBOUND) {
            return inventoryManagementService.outboundInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getQuantity()
            );
        } else if (movement.getMovementType() == InventoryMovement.MovementType.ADJUSTMENT) {
            return inventoryManagementService.adjustInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getQuantity()
            );
        } else if (movement.getMovementType() == InventoryMovement.MovementType.TRANSFER) {
            return transferInventory(warehouseId, movement);
        } else {
            throw new ValidationException("不支持的库存移动类型: " + movement.getMovementType());
        }
    }

    /**
     * 库存调拨：源仓库出库 + 目标仓库入库，整体在一个事务内完成
     */
    private InventoryRecord transferInventory(String warehouseId, InventoryMovement movement) {
        String targetWarehouseId = movement.getTargetWarehouseId();
        if (targetWarehouseId == null || targetWarehouseId.isBlank()) {
            throw new ValidationException("库存调拨必须指定目标仓库");
        }
        requireWarehouse(targetWarehouseId);
        if (movement.getQuantity() <= 0) {
            throw new ValidationException("调拨数量必须大于0");
        }
        String productId = movement.getProductId();
        String sku = movement.getSku() != null ? movement.getSku() : productId;
        String productKey = productId != null && !productId.isBlank() ? productId : sku;
        inventoryManagementService.outboundInventory(warehouseId, productKey, movement.getQuantity());
        return inventoryManagementService.inboundInventory(
                targetWarehouseId, productKey, sku, movement.getQuantity(), null);
    }

    private Warehouse getWarehouse(String warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));
    }

    private void requireWarehouse(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) {
            throw new ValidationException("仓库ID不能为空");
        }
        getWarehouse(warehouseId);
    }

    private void publishDomainEvents(Warehouse warehouse) {
        if (warehouse != null) {
            domainEventPublisher.publishEventsFrom(warehouse);
        }
    }
}
