package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.platform.PlatformIntegrationApplicationService;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;
import java.util.UUID;

/**
 * 平台集成控制器，用于处理平台集成相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/platform-integrations")
@Tag(name = "平台集成管理", description = "平台集成相关的API接口")
public class PlatformIntegrationController {

    private final PlatformIntegrationApplicationService platformIntegrationApplicationService;

    @Autowired
    public PlatformIntegrationController(PlatformIntegrationApplicationService platformIntegrationApplicationService) {
        this.platformIntegrationApplicationService = platformIntegrationApplicationService;
    }

    @Operation(summary = "创建平台集成", description = "创建一个新的平台集成")
    @PostMapping
    public ResponseEntity<Result<PlatformIntegration>> createPlatformIntegration(@Parameter(description = "平台集成信息") @RequestBody PlatformIntegration platformIntegration) {
        try {
            PlatformIntegration created = platformIntegrationApplicationService.createPlatformIntegration(platformIntegration);
            return ResponseEntity.ok(Result.success(created));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成创建失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "根据ID获取平台集成", description = "根据平台集成ID获取详细信息")
    @GetMapping("/{id}")
    public ResponseEntity<Result<PlatformIntegration>> getPlatformIntegrationById(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            PlatformIntegration platform = platformIntegrationApplicationService.getPlatformIntegrationById(id);
            if (platform != null) {
                return ResponseEntity.ok(Result.success(platform));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成查询失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "获取所有平台集成", description = "获取所有平台集成的列表")
    @GetMapping
    public ResponseEntity<Result<List<PlatformIntegration>>> getAllPlatformIntegrations() {
        try {
            List<PlatformIntegration> platforms = platformIntegrationApplicationService.getAllPlatformIntegrations();
            return ResponseEntity.ok(Result.success(platforms));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成列表查询失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "更新平台集成", description = "根据ID更新平台集成信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<PlatformIntegration>> updatePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id, @Parameter(description = "平台集成信息") @RequestBody PlatformIntegration platformIntegration) {
        try {
            PlatformIntegration updated = platformIntegrationApplicationService.updatePlatformIntegration(id, platformIntegration);
            if (updated != null) {
                return ResponseEntity.ok(Result.success(updated));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成更新失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "删除平台集成", description = "根据ID删除平台集成")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deletePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            platformIntegrationApplicationService.deletePlatformIntegration(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成删除失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "激活平台集成", description = "激活指定ID的平台集成")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Result<PlatformIntegration>> activatePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            PlatformIntegration activated = platformIntegrationApplicationService.activatePlatformIntegration(id);
            if (activated != null) {
                return ResponseEntity.ok(Result.success(activated));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成激活失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "停用平台集成", description = "停用指定ID的平台集成")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Result<PlatformIntegration>> deactivatePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            PlatformIntegration deactivated = platformIntegrationApplicationService.deactivatePlatformIntegration(id);
            if (deactivated != null) {
                return ResponseEntity.ok(Result.success(deactivated));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成停用失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步订单", description = "从平台同步订单")
    @PostMapping("/{id}/sync-orders")
    public ResponseEntity<Result<Void>> syncOrdersFromPlatform(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            platformIntegrationApplicationService.syncOrdersFromPlatform(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("订单同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步产品", description = "从平台同步产品")
    @PostMapping("/{id}/sync-products")
    public ResponseEntity<Result<Void>> syncProductsFromPlatform(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            platformIntegrationApplicationService.syncProductsFromPlatform(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("产品同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步库存", description = "向平台同步库存")
    @PostMapping("/{id}/sync-inventory")
    public ResponseEntity<Result<Void>> syncInventoryToPlatform(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        try {
            platformIntegrationApplicationService.syncInventoryToPlatform(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("库存同步失败: " + e.getMessage()));
        }
    }
}
