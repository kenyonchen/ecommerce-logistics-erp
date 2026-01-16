package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 仓储控制器，处理仓储和库存相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/warehouses")
@Tag(name = "仓储管理", description = "仓储和库存相关的API接口")
public class WarehouseController {
    private final WarehouseApplicationService warehouseApplicationService;

    public WarehouseController(WarehouseApplicationService warehouseApplicationService) {
        this.warehouseApplicationService = warehouseApplicationService;
    }

    @Operation(summary = "创建仓库", description = "创建一个新的仓库")
    @PostMapping
    public Result<Warehouse> createWarehouse(
            @RequestParam(defaultValue = "default-tenant") String tenantId,
            @RequestParam String warehouseName,
            @RequestParam String warehouseCode,
            @RequestParam String address,
            @RequestParam int capacity) {
        var warehouse = warehouseApplicationService.createWarehouse(tenantId, warehouseName, warehouseCode, address, capacity);
        return Result.success(warehouse);
    }

    @Operation(summary = "根据ID获取仓库", description = "根据仓库ID获取仓库详细信息")
    @GetMapping("/{id}")
    public Result<Warehouse> getWarehouseById(
            @Parameter(description = "仓库ID") @PathVariable String id) {
        var warehouse = warehouseApplicationService.findWarehouseById(id);
        return Result.success(warehouse);
    }

    @Operation(summary = "获取所有仓库", description = "获取所有仓库的列表")
    @GetMapping
    public Result<List<Warehouse>> getAllWarehouses() {
        var warehouses = warehouseApplicationService.findAllWarehouses();
        return Result.success(warehouses);
    }

    @Operation(summary = "根据仓库ID获取库存记录", description = "根据仓库ID获取所有库存记录")
    @GetMapping("/{warehouseId}/inventory")
    public Result<List<InventoryRecord>> getInventoryByWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId) {
        var inventoryRecords = warehouseApplicationService.findInventoryByWarehouse(warehouseId);
        return Result.success(inventoryRecords);
    }

    @Operation(summary = "执行库存盘点", description = "对指定仓库和产品进行库存盘点")
    @PostMapping("/{warehouseId}/inventory/count")
    public Result<InventoryRecord> countInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestParam String productId,
            @RequestParam int actualQuantity) {
        var inventoryRecord = warehouseApplicationService.countInventory(warehouseId, productId, actualQuantity);
        return Result.success(inventoryRecord);
    }

    @Operation(summary = "执行库存移动", description = "在仓库内移动库存")
    @PostMapping("/{warehouseId}/inventory/move")
    public Result<InventoryRecord> moveInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMovement movement) {
        var inventoryRecord = warehouseApplicationService.moveInventory(warehouseId, movement);
        return Result.success(inventoryRecord);
    }
}
