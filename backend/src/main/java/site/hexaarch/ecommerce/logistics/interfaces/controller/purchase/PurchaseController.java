package site.hexaarch.ecommerce.logistics.interfaces.controller.purchase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.purchase.PurchaseApprovalRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.purchase.PurchaseCreateRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.purchase.PurchaseItemRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.purchase.PurchaseOrderDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购控制器，处理采购相关的HTTP请求。
 * <p>
 * 出参统一为 {@link PurchaseOrderDto}，入参统一为 JSON 请求体；业务失败不再在本类捕获，
 * 交由 GlobalExceptionHandler 映射成 400/404（此前每个方法 try/catch 会把失败压成 HTTP 200 + code=500）。
 * </p>
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
     * 创建采购单（单头 + 明细行一次提交）
     */
    @Operation(summary = "创建采购单", description = "创建一个新的采购单，可同时提交明细行")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_CREATE, label = "新建采购单", targetType = "PurchaseOrder")
    @PostMapping
    public Result<PurchaseOrderDto> createPurchaseOrder(@RequestBody PurchaseCreateRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PurchaseOrder purchaseOrder = purchaseApplicationService.createPurchaseOrder(tenantId,
                request.getSupplierId(), request.getSupplierName(), request.getWarehouseId(),
                request.toDomainItems());
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 分页 + 条件查询采购单
     */
    @Operation(summary = "分页查询采购单", description = "按采购单号/供应商/仓库/状态/金额下限/创建日期区间分页查询")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read')")
    @GetMapping
    public Result<PageResult<PurchaseOrderDto>> searchPurchaseOrders(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "采购单号（模糊）") @RequestParam(required = false) String purchaseOrderId,
            @Parameter(description = "供应商ID") @RequestParam(required = false) String supplierId,
            @Parameter(description = "供应商名称（模糊）") @RequestParam(required = false) String supplierName,
            @Parameter(description = "收货仓库ID") @RequestParam(required = false) String warehouseId,
            @Parameter(description = "采购状态") @RequestParam(required = false) PurchaseStatus status,
            @Parameter(description = "总金额下限（含）") @RequestParam(required = false) BigDecimal minTotalAmount,
            @Parameter(description = "创建日期起(yyyy-MM-dd)") @RequestParam(required = false) String startDate,
            @Parameter(description = "创建日期止(yyyy-MM-dd)") @RequestParam(required = false) String endDate,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        PageResult<PurchaseOrder> purchaseOrders = purchaseApplicationService.searchPurchaseOrders(
                resolveTenant(tenantId), purchaseOrderId, supplierId, supplierName, warehouseId, status,
                minTotalAmount, startDate, endDate, page == null ? 0 : page, size == null ? 10 : size);
        return Result.success(toDtoPage(purchaseOrders));
    }

    /**
     * 采购状态字典
     */
    @Operation(summary = "采购状态字典", description = "返回全部采购状态及其中文名称，前端下拉框使用")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read')")
    @GetMapping("/statuses")
    public Result<List<TargetFieldDto>> statuses() {
        List<TargetFieldDto> statuses = new ArrayList<>();
        for (PurchaseStatus status : PurchaseStatus.values()) {
            statuses.add(new TargetFieldDto(status.name(), status.getDescription(), Boolean.FALSE));
        }
        return Result.success(statuses);
    }

    /**
     * 根据采购单号查找采购单
     */
    @Operation(summary = "根据ID获取采购单", description = "根据采购单ID获取详细信息")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read')")
    @GetMapping("/{purchaseOrderId}")
    public Result<PurchaseOrderDto> findPurchaseOrderById(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.findPurchaseOrderById(resolveTenant(tenantId), purchaseOrderId);
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 添加采购项到采购单
     */
    @Operation(summary = "添加采购项", description = "向采购单添加新的采购项（仅待处理状态可维护明细）")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_ITEM_ADD, label = "添加采购明细行", targetType = "PurchaseOrder")
    @PostMapping("/{purchaseOrderId}/items")
    public Result<PurchaseOrderDto> addPurchaseOrderItem(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @RequestBody PurchaseItemRequest request) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.addPurchaseOrderItem(resolveTenant(tenantId),
                purchaseOrderId, request.getSkuCode(), request.getProductName(), request.getQuantity(),
                request.getUnitPrice());
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 修改采购项
     */
    @Operation(summary = "修改采购项", description = "修改采购项的数量与单价，小计与总金额由服务端重算")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_ITEM_UPDATE, label = "修改采购明细行数量与单价", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/items/{itemId}")
    public Result<PurchaseOrderDto> updatePurchaseOrderItem(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "采购项ID") @PathVariable String itemId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @RequestBody PurchaseItemRequest request) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.updatePurchaseOrderItem(resolveTenant(tenantId),
                purchaseOrderId, itemId, request.getQuantity(), request.getUnitPrice());
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 删除采购项
     */
    @Operation(summary = "删除采购项", description = "从采购单删除指定采购项")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_ITEM_REMOVE, label = "删除采购明细行", targetType = "PurchaseOrder")
    @DeleteMapping("/{purchaseOrderId}/items/{itemId}")
    public Result<PurchaseOrderDto> removePurchaseOrderItem(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "采购项ID") @PathVariable String itemId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.removePurchaseOrderItem(resolveTenant(tenantId),
                purchaseOrderId, itemId);
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 批准采购单
     */
    @Operation(summary = "批准采购单", description = "批准指定ID的采购单，记录审批人与审批意见")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_APPROVE, label = "批准采购单", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/approve")
    public Result<PurchaseOrderDto> approvePurchaseOrder(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @RequestBody(required = false) PurchaseApprovalRequest request) {
        PurchaseApprovalRequest approval = request == null ? new PurchaseApprovalRequest() : request;
        PurchaseOrder purchaseOrder = purchaseApplicationService.approvePurchaseOrder(resolveTenant(tenantId),
                purchaseOrderId, resolveApprover(approval), approval.getComment());
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 拒绝采购单
     */
    @Operation(summary = "拒绝采购单", description = "拒绝指定ID的采购单，必须填写拒绝原因")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_REJECT, label = "拒绝采购单", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/reject")
    public Result<PurchaseOrderDto> rejectPurchaseOrder(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @RequestBody(required = false) PurchaseApprovalRequest request) {
        PurchaseApprovalRequest approval = request == null ? new PurchaseApprovalRequest() : request;
        if (approval.getComment() == null || approval.getComment().isBlank()) {
            throw new ValidationException("拒绝采购单必须填写拒绝原因");
        }
        PurchaseOrder purchaseOrder = purchaseApplicationService.rejectPurchaseOrder(resolveTenant(tenantId),
                purchaseOrderId, resolveApprover(approval), approval.getComment());
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 标记采购单为运输中
     */
    @Operation(summary = "标记采购单为运输中", description = "标记指定ID的采购单为运输中")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_TRANSIT, label = "采购单标记为运输中", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/in-transit")
    public Result<PurchaseOrderDto> markPurchaseOrderAsInTransit(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.markPurchaseOrderAsInTransit(resolveTenant(tenantId),
                purchaseOrderId);
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 标记采购单为已送达（触发到货入库与采购应付）
     */
    @Operation(summary = "标记采购单为已送达",
            description = "标记指定ID的采购单为已送达，同事务内完成收货入库与采购应付入账")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_DELIVERED, label = "采购单确认到货（入库并记应付）", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/delivered")
    public Result<PurchaseOrderDto> markPurchaseOrderAsDelivered(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.markPurchaseOrderAsDelivered(resolveTenant(tenantId),
                purchaseOrderId);
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    /**
     * 取消采购单
     */
    @Operation(summary = "取消采购单", description = "取消指定ID的采购单（仅待处理/已批准可取消）")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write')")
    @Auditable(action = AuditActions.PURCHASE_CANCEL, label = "取消采购单", targetType = "PurchaseOrder")
    @PutMapping("/{purchaseOrderId}/cancel")
    public Result<PurchaseOrderDto> cancelPurchaseOrder(
            @Parameter(description = "采购单ID") @PathVariable String purchaseOrderId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        PurchaseOrder purchaseOrder = purchaseApplicationService.cancelPurchaseOrder(resolveTenant(tenantId),
                purchaseOrderId);
        return Result.success(PurchaseOrderDto.from(purchaseOrder));
    }

    private PurchaseOrderDto toDto(PurchaseOrder purchaseOrder) {
        return PurchaseOrderDto.from(purchaseOrder);
    }

    private PageResult<PurchaseOrderDto> toDtoPage(PageResult<PurchaseOrder> purchaseOrders) {
        List<PurchaseOrderDto> content = new ArrayList<>();
        for (PurchaseOrder purchaseOrder : purchaseOrders.getContent()) {
            content.add(toDto(purchaseOrder));
        }
        return new PageResult<>(content, purchaseOrders.getTotal(), purchaseOrders.getPage(), purchaseOrders.getSize());
    }

    /**
     * 租户解析：优先取请求参数，其次取租户上下文，最后回退默认租户
     */
    private String resolveTenant(String tenantId) {
        String requested = TenantContext.normalizeRequested(tenantId);
        if (requested != null) {
            return requested;
        }
        if (TenantContext.hasTenantId()) {
            return TenantContext.getTenantId();
        }
        return PurchaseApplicationService.DEFAULT_TENANT_ID;
    }

    /**
     * 审批人：请求体未显式指定时取登录账号，匿名（单测）下记为 SYSTEM
     */
    private String resolveApprover(PurchaseApprovalRequest request) {
        if (request.getApprovedBy() != null && !request.getApprovedBy().isBlank()) {
            return request.getApprovedBy().trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "SYSTEM";
        }
        return authentication.getName();
    }
}
