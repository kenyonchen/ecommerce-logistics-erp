package site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseApplicationService;
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
@RequiredArgsConstructor
@Tag(name = "仓储管理", description = "仓储和库存相关的API接口")
public class WarehouseController {
    private final WarehouseApplicationService warehouseApplicationService;

    @Data
    public static class CreateWarehouseRequest {
        private String tenantId;
        private String warehouseName;
        private String warehouseCode;
        private String address;
        private Integer capacity;
    }

    @Data
    public static class UpdateWarehouseRequest {
        private String warehouseName;
        private String warehouseCode;
        private String address;
        private Integer capacity;
    }

    @Data
    public static class AddLocationRequest {
        private String locationCode;
        private String locationType;
        private Integer capacity;
    }

    @Data
    public static class InventoryMoveRequest {
        private String sku;
        private String productId;
        private Integer quantity;
        private String movementType;
        private String targetWarehouseId;
        private String reason;
    }

    @Operation(summary = "创建仓库", description = "创建一个新的仓库")
    @PostMapping
    public Result<Warehouse> createWarehouse(@RequestBody CreateWarehouseRequest request) {
        var warehouse = warehouseApplicationService.createWarehouse(
                request.getTenantId(),
                request.getWarehouseName(),
                request.getWarehouseCode(),
                request.getAddress(),
                request.getCapacity() == null ? 1 : request.getCapacity());
        return Result.success(warehouse);
    }

    @Operation(summary = "根据ID获取仓库", description = "根据仓库ID获取仓库详细信息")
    @GetMapping("/{id}")
    public Result<Warehouse> getWarehouseById(
            @Parameter(description = "仓库ID") @PathVariable String id) {
        return Result.success(warehouseApplicationService.findWarehouseById(id));
    }

    @Operation(summary = "根据编码获取仓库", description = "根据仓库编码获取仓库详细信息")
    @GetMapping("/code/{code}")
    public Result<Warehouse> getWarehouseByCode(
            @Parameter(description = "仓库编码") @PathVariable String code) {
        return Result.success(warehouseApplicationService.findWarehouseByCode(code));
    }

    @Operation(summary = "获取所有仓库", description = "获取所有仓库的列表")
    @GetMapping
    public Result<List<Warehouse>> getAllWarehouses() {
        return Result.success(warehouseApplicationService.findAllWarehouses());
    }

    @Operation(summary = "更新仓库", description = "更新仓库基础信息")
    @PutMapping("/{id}")
    public Result<Warehouse> updateWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String id,
            @RequestBody UpdateWarehouseRequest request) {
        Warehouse existing = warehouseApplicationService.findWarehouseById(id);
        var warehouse = warehouseApplicationService.updateWarehouse(
                id,
                request.getWarehouseName() != null ? request.getWarehouseName() : existing.getWarehouseName(),
                request.getWarehouseCode() != null ? request.getWarehouseCode() : existing.getWarehouseCode(),
                request.getAddress() != null ? request.getAddress() : existing.getAddress(),
                request.getCapacity() != null ? request.getCapacity() : existing.getCapacity());
        return Result.success(warehouse);
    }

    @Operation(summary = "删除仓库", description = "删除指定仓库（存在库存时禁止删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String id) {
        warehouseApplicationService.deleteWarehouse(id);
        return Result.success(null);
    }

    @Operation(summary = "变更仓库状态", description = "启用或停用仓库")
    @PutMapping("/{id}/status")
    public Result<Warehouse> changeStatus(
            @Parameter(description = "仓库ID") @PathVariable String id,
            @Parameter(description = "是否启用") @RequestParam boolean active) {
        return Result.success(warehouseApplicationService.changeWarehouseStatus(id, active));
    }

    @Operation(summary = "添加库位", description = "为指定仓库添加一个库位")
    @PostMapping("/{id}/locations")
    public Result<Warehouse> addLocation(
            @Parameter(description = "仓库ID") @PathVariable String id,
            @RequestBody AddLocationRequest request) {
        return Result.success(warehouseApplicationService.addLocation(
                id, request.getLocationCode(), request.getLocationType(), request.getCapacity()));
    }

    @Operation(summary = "根据仓库ID获取库存记录", description = "根据仓库ID获取所有库存记录")
    @GetMapping("/{warehouseId}/inventory")
    public Result<List<InventoryRecord>> getInventoryByWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId) {
        return Result.success(warehouseApplicationService.findInventoryByWarehouse(warehouseId));
    }

    @Operation(summary = "库存入库", description = "向指定仓库入库指定SKU")
    @PostMapping("/{warehouseId}/inventory/in")
    public Result<InventoryRecord> inventoryIn(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMoveRequest request) {
        return Result.success(warehouseApplicationService.inventoryIn(
                request.getSku() != null ? request.getSku() : request.getProductId(),
                warehouseId, request.getQuantity()));
    }

    @Operation(summary = "库存出库", description = "从指定仓库出库指定SKU")
    @PostMapping("/{warehouseId}/inventory/out")
    public Result<InventoryRecord> inventoryOut(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMoveRequest request) {
        return Result.success(warehouseApplicationService.inventoryOut(
                request.getSku() != null ? request.getSku() : request.getProductId(),
                warehouseId, request.getQuantity()));
    }

    @Operation(summary = "执行库存盘点", description = "对指定仓库和产品进行库存盘点")
    @PostMapping("/{warehouseId}/inventory/count")
    public Result<InventoryRecord> countInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestParam String productId,
            @RequestParam int actualQuantity) {
        return Result.success(warehouseApplicationService.countInventory(warehouseId, productId, actualQuantity));
    }

    @Operation(summary = "执行库存移动", description = "在仓库内移动库存（支持入库/出库/调整/调拨）")
    @PostMapping("/{warehouseId}/inventory/move")
    public Result<InventoryRecord> moveInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMovement movement) {
        return Result.success(warehouseApplicationService.moveInventory(warehouseId, movement));
    }
}
