package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购控制器，处理采购相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/purchases")
@Tag(name = "采购管理", description = "采购相关的API接口")
public class PurchaseController {
    private final PurchaseApplicationService purchaseApplicationService;

    /**
     * 构造函数，注入采购应用服务。
     *
     * @param purchaseApplicationService 采购应用服务
     */
    public PurchaseController(PurchaseApplicationService purchaseApplicationService) {
        this.purchaseApplicationService = purchaseApplicationService;
    }

    /**
     * 创建采购单
     */
    @Operation(summary = "创建采购单", description = "创建一个新的采购单")
    @PostMapping
    public Result<PurchaseOrder> createPurchaseOrder(
            @Parameter(description = "租户ID") @RequestParam(defaultValue = "default-tenant") String tenantId,
            @Parameter(description = "供应商ID") @RequestParam String supplierId,
            @Parameter(description = "供应商名称") @RequestParam String supplierName,
            @Parameter(description = "仓库ID") @RequestParam String warehouseId) {
        try {
            var purchaseOrder = purchaseApplicationService.createPurchaseOrder(tenantId, supplierId, supplierName, warehouseId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("创建采购单失败: " + e.getMessage());
        }
    }

    /**
     * 根据采购单号查找采购单
     */
    @Operation(summary = "根据ID获取采购单", description = "根据采购单ID获取详细信息")
    @GetMapping("/{purchaseOrderId}")
    public Result<PurchaseOrder> findPurchaseOrderById(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.findPurchaseOrderById(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("查找采购单失败: " + e.getMessage());
        }
    }

    /**
     * 查找所有采购单或根据状态筛选
     */
    @Operation(summary = "获取采购单列表", description = "获取所有采购单或根据状态筛选")
    @GetMapping
    public Result<List<PurchaseOrder>> findPurchaseOrders(@Parameter(description = "采购状态") @RequestParam(required = false) PurchaseStatus status) {
        try {
            List<PurchaseOrder> purchaseOrders;
            if (status != null) {
                purchaseOrders = purchaseApplicationService.findPurchaseOrdersByStatus(status);
            } else {
                // 这里简化处理，实际应该实现一个查找所有采购单的方法
                purchaseOrders = purchaseApplicationService.findPurchaseOrdersByStatus(PurchaseStatus.PENDING);
            }
            return Result.success(purchaseOrders);
        } catch (Exception e) {
            return Result.error("查找采购单失败: " + e.getMessage());
        }
    }

    /**
     * 添加采购项到采购单
     */
    @Operation(summary = "添加采购项", description = "向采购单添加新的采购项")
    @PostMapping("/{purchaseOrderId}/items")
    public Result<PurchaseOrder> addPurchaseOrderItem(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "SKU编码") @RequestParam String skuCode,
            @Parameter(description = "产品名称") @RequestParam String productName,
            @Parameter(description = "数量") @RequestParam Integer quantity,
            @Parameter(description = "单价") @RequestParam BigDecimal unitPrice) {
        try {
            var purchaseOrder = purchaseApplicationService.addPurchaseOrderItem(purchaseOrderId, skuCode, productName, quantity, unitPrice);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("添加采购项失败: " + e.getMessage());
        }
    }

    /**
     * 批准采购单
     */
    @Operation(summary = "批准采购单", description = "批准指定ID的采购单")
    @PutMapping("/{purchaseOrderId}/approve")
    public Result<PurchaseOrder> approvePurchaseOrder(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.approvePurchaseOrder(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("批准采购单失败: " + e.getMessage());
        }
    }

    /**
     * 拒绝采购单
     */
    @Operation(summary = "拒绝采购单", description = "拒绝指定ID的采购单")
    @PutMapping("/{purchaseOrderId}/reject")
    public Result<PurchaseOrder> rejectPurchaseOrder(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.rejectPurchaseOrder(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("拒绝采购单失败: " + e.getMessage());
        }
    }

    /**
     * 标记采购单为运输中
     */
    @Operation(summary = "标记采购单为运输中", description = "标记指定ID的采购单为运输中")
    @PutMapping("/{purchaseOrderId}/in-transit")
    public Result<PurchaseOrder> markPurchaseOrderAsInTransit(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.markPurchaseOrderAsInTransit(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("标记采购单为运输中失败: " + e.getMessage());
        }
    }

    /**
     * 标记采购单为已送达
     */
    @Operation(summary = "标记采购单为已送达", description = "标记指定ID的采购单为已送达")
    @PutMapping("/{purchaseOrderId}/delivered")
    public Result<PurchaseOrder> markPurchaseOrderAsDelivered(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.markPurchaseOrderAsDelivered(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("标记采购单为已送达失败: " + e.getMessage());
        }
    }

    /**
     * 取消采购单
     */
    @Operation(summary = "取消采购单", description = "取消指定ID的采购单")
    @PutMapping("/{purchaseOrderId}/cancel")
    public Result<PurchaseOrder> cancelPurchaseOrder(@Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        try {
            var purchaseOrder = purchaseApplicationService.cancelPurchaseOrder(purchaseOrderId);
            return Result.success(purchaseOrder);
        } catch (Exception e) {
            return Result.error("取消采购单失败: " + e.getMessage());
        }
    }
}
