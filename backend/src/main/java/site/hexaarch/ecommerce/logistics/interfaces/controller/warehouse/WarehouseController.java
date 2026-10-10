package site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
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
    private static final String HAS_WAREHOUSE_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:read')";
    private static final String HAS_WAREHOUSE_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:write')";

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
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.WAREHOUSE_CREATE, label = "创建仓库", targetType = "Warehouse")
    @PostMapping
    public Result<Warehouse> createWarehouse(@RequestBody CreateWarehouseRequest request) {
        var warehouse = warehouseApplicationService.createWarehouse(
                // 与同控制器的列表/更新侧（resolveTenant）保持同一口径，否则缺省 tenantId 的仓
                // 落进 default-tenant 桶，登录租户既看不见也删不掉（ISS-118）。
                resolveTenant(request.getTenantId()),
                request.getWarehouseName(),
                request.getWarehouseCode(),
                request.getAddress(),
                request.getCapacity() == null ? 1 : request.getCapacity());
        return Result.success(warehouse);
    }

    @Operation(summary = "根据ID获取仓库", description = "根据仓库ID获取仓库详细信息")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/{id}")
    public Result<Warehouse> getWarehouseById(
            @Parameter(description = "仓库ID") @PathVariable String id) {
        return Result.success(warehouseApplicationService.findWarehouseById(id));
    }

    @Operation(summary = "根据编码获取仓库", description = "根据仓库编码获取仓库详细信息")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/code/{code}")
    public Result<Warehouse> getWarehouseByCode(
            @Parameter(description = "仓库编码") @PathVariable String code) {
        return Result.success(warehouseApplicationService.findWarehouseByCode(code));
    }

    @Operation(summary = "分页查询仓库", description = "按租户分页查询仓库列表（ISS-050：tenantId 缺省时取 JWT 上下文租户）")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping
    public Result<PageResult<Warehouse>> getAllWarehouses(
            @Parameter(description = "租户ID（可空，缺省取当前登录租户）") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(warehouseApplicationService.searchWarehouses(resolveTenant(tenantId), page, size));
    }

    private String resolveTenant(String tenantId) {
        String requested = TenantContext.normalizeRequested(tenantId);
        if (requested != null) {
            return requested;
        }
        if (TenantContext.hasTenantId()) {
            return TenantContext.getTenantId();
        }
        return WarehouseApplicationService.DEFAULT_TENANT_ID;
    }

    @Operation(summary = "更新仓库", description = "更新仓库基础信息")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.WAREHOUSE_UPDATE, label = "更新仓库基础信息", targetType = "Warehouse")
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

    @Operation(summary = "删除仓库", description = "删除指定仓库（存在库存、被采购单引用或已绑定店铺仓库时禁止删除）")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.WAREHOUSE_DELETE, label = "删除仓库", targetType = "Warehouse")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String id) {
        warehouseApplicationService.deleteWarehouse(id);
        return Result.success(null);
    }

    @Operation(summary = "变更仓库状态", description = "启用或停用仓库")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.WAREHOUSE_STATUS_UPDATE, label = "启用或停用仓库", targetType = "Warehouse")
    @PutMapping("/{id}/status")
    public Result<Warehouse> changeStatus(
            @Parameter(description = "仓库ID") @PathVariable String id,
            @Parameter(description = "是否启用") @RequestParam boolean active) {
        return Result.success(warehouseApplicationService.changeWarehouseStatus(id, active));
    }

    @Operation(summary = "添加库位", description = "为指定仓库添加一个库位")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.WAREHOUSE_LOCATION_ADD, label = "添加库位", targetType = "Warehouse")
    @PostMapping("/{id}/locations")
    public Result<Warehouse> addLocation(
            @Parameter(description = "仓库ID") @PathVariable String id,
            @RequestBody AddLocationRequest request) {
        return Result.success(warehouseApplicationService.addLocation(
                id, request.getLocationCode(), request.getLocationType(), request.getCapacity()));
    }

    @Operation(summary = "根据仓库ID获取库存记录", description = "根据仓库ID获取所有库存记录")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/{warehouseId}/inventory")
    public Result<List<InventoryRecord>> getInventoryByWarehouse(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId) {
        return Result.success(warehouseApplicationService.findInventoryByWarehouse(warehouseId));
    }

    @Operation(summary = "库存入库", description = "向指定仓库入库指定SKU")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.INVENTORY_IN, label = "库存入库", targetType = "Inventory")
    @PostMapping("/{warehouseId}/inventory/in")
    public Result<InventoryRecord> inventoryIn(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMoveRequest request) {
        return Result.success(warehouseApplicationService.inventoryIn(
                request.getSku() != null ? request.getSku() : request.getProductId(),
                warehouseId, request.getQuantity()));
    }

    @Operation(summary = "库存出库", description = "从指定仓库出库指定SKU")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.INVENTORY_OUT, label = "库存出库", targetType = "Inventory")
    @PostMapping("/{warehouseId}/inventory/out")
    public Result<InventoryRecord> inventoryOut(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMoveRequest request) {
        return Result.success(warehouseApplicationService.inventoryOut(
                request.getSku() != null ? request.getSku() : request.getProductId(),
                warehouseId, request.getQuantity()));
    }

    @Operation(summary = "执行库存盘点", description = "对指定仓库和产品进行库存盘点")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.INVENTORY_COUNT, label = "库存盘点", targetType = "Inventory", targetIdParam = "productId")
    @PostMapping("/{warehouseId}/inventory/count")
    public Result<InventoryRecord> countInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestParam String productId,
            @RequestParam int actualQuantity) {
        return Result.success(warehouseApplicationService.countInventory(warehouseId, productId, actualQuantity));
    }

    @Operation(summary = "执行库存移动", description = "在仓库内移动库存（支持入库/出库/调整/调拨）")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.INVENTORY_MOVE, label = "库存移动（入库/出库/调整/调拨）", targetType = "Inventory")
    @PostMapping("/{warehouseId}/inventory/move")
    public Result<InventoryRecord> moveInventory(
            @Parameter(description = "仓库ID") @PathVariable String warehouseId,
            @RequestBody InventoryMovement movement) {
        return Result.success(warehouseApplicationService.moveInventory(warehouseId, movement));
    }
}
