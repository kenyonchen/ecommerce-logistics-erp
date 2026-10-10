package site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.ShopWarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.BindShopWarehouseRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseDto;

import java.util.List;
import java.util.Map;

/**
 * 店铺仓库控制器：第三方电商平台店铺仓库与本系统本地仓库的对接管理。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/warehouses/shop")
@RequiredArgsConstructor
@Tag(name = "店铺仓库对接", description = "第三方平台店铺仓库同步、绑定与管理")
public class ShopWarehouseController {
    private static final String HAS_SHOP_SYNC =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('shop:sync')";
    private static final String HAS_WAREHOUSE_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:read')";
    private static final String HAS_WAREHOUSE_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:write')";


    private final ShopWarehouseApplicationService shopWarehouseApplicationService;

    @Operation(summary = "查询店铺仓库列表", description = "按租户/平台/本地仓库过滤店铺仓库绑定关系")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping
    public Result<List<ShopWarehouseDto>> listShopWarehouses(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "本地仓库ID") @RequestParam(required = false) String localWarehouseId) {
        return Result.success(shopWarehouseApplicationService.listShopWarehouses(tenantId, platform, localWarehouseId));
    }

    @Operation(summary = "查询店铺仓库详情", description = "按ID查询店铺仓库绑定关系")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/{id}")
    public Result<ShopWarehouseDto> getShopWarehouse(
            @Parameter(description = "店铺仓库ID") @PathVariable String id) {
        return Result.success(shopWarehouseApplicationService.getShopWarehouse(id));
    }

    @Operation(summary = "绑定本地仓库", description = "将平台店铺仓库绑定到本系统仓库（已存在时更新绑定）")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.SHOP_WAREHOUSE_BIND, label = "绑定店铺仓库", targetType = "ShopWarehouse")
    @PostMapping("/bind")
    public Result<ShopWarehouseDto> bindShopWarehouse(@RequestBody BindShopWarehouseRequest request) {
        return Result.success(shopWarehouseApplicationService.bindShopWarehouse(request));
    }

    @Operation(summary = "解绑本地仓库", description = "解除店铺仓库与本地仓库的绑定关系")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.SHOP_WAREHOUSE_UNBIND, label = "解绑店铺仓库", targetType = "ShopWarehouse")
    @PostMapping("/{id}/unbind")
    public Result<ShopWarehouseDto> unbindShopWarehouse(
            @Parameter(description = "店铺仓库ID") @PathVariable String id) {
        return Result.success(shopWarehouseApplicationService.unbindShopWarehouse(id));
    }

    @Operation(summary = "从平台同步店铺仓库", description = "从第三方平台拉取店铺仓库数据并按本地仓库编码自动绑定")
    @PreAuthorize(HAS_SHOP_SYNC)
    @Auditable(action = AuditActions.SHOP_WAREHOUSE_SYNC, label = "从平台同步店铺仓库", targetType = "ShopWarehouse", targetIdParam = "integrationId")
    @PostMapping("/sync/{integrationId}")
    public Result<Map<String, Object>> syncFromPlatform(
            @Parameter(description = "平台集成ID") @PathVariable String integrationId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "是否使用示例数据") @RequestParam(required = false) Boolean useSampleData,
            @Parameter(description = "关键字过滤") @RequestParam(required = false) String keyword,
            @Parameter(description = "拉取条数") @RequestParam(required = false) Integer limit) {
        return Result.success(shopWarehouseApplicationService.syncShopWarehousesFromPlatform(
                tenantId, integrationId, useSampleData, keyword, limit));
    }
}
