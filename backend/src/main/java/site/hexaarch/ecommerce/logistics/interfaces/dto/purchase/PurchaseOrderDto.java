package site.hexaarch.ecommerce.logistics.interfaces.dto.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购单出参。
 * <p>
 * 取代直接把 {@link PurchaseOrder} 聚合当 REST 契约的做法（docs/qa/known-issues.md ISS-063）：
 * 聚合内部的待发事件队列不再出现在响应里，事件类改名也不再是破坏性变更。
 * </p>
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "采购单信息")
public class PurchaseOrderDto {

    @Schema(description = "采购单号")
    private String purchaseOrderId;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "供应商ID")
    private String supplierId;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "收货仓库ID")
    private String warehouseId;

    @Schema(description = "采购状态（枚举名：PENDING/APPROVED/REJECTED/IN_TRANSIT/DELIVERED/CANCELLED）")
    private PurchaseStatus status;

    @Schema(description = "采购状态中文名")
    private String statusLabel;

    @Schema(description = "可流转的下一状态（枚举名），前端据此渲染动作按钮")
    private List<String> allowedNextStatuses;

    @Schema(description = "总金额 = 明细小计之和")
    private BigDecimal totalAmount;

    @Schema(description = "采购明细行")
    private List<PurchaseOrderItemDto> items;

    @Schema(description = "审批人")
    private String approvedBy;

    @Schema(description = "审批时间")
    private LocalDateTime approvedAt;

    @Schema(description = "审批意见 / 拒绝原因")
    private String approvalComment;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * 由采购单聚合构造DTO。
     *
     * @param purchaseOrder 采购单聚合
     * @return DTO，入参为null时返回null
     */
    public static PurchaseOrderDto from(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPurchaseOrderId(purchaseOrder.getPurchaseOrderId());
        dto.setTenantId(purchaseOrder.getTenantId());
        dto.setSupplierId(purchaseOrder.getSupplierId());
        dto.setSupplierName(purchaseOrder.getSupplierName());
        dto.setWarehouseId(purchaseOrder.getWarehouseId());
        dto.setTotalAmount(purchaseOrder.getTotalAmount());
        dto.setCreatedAt(purchaseOrder.getCreatedAt());
        dto.setUpdatedAt(purchaseOrder.getUpdatedAt());
        dto.setApprovedBy(purchaseOrder.getApprovedBy());
        dto.setApprovedAt(purchaseOrder.getApprovedAt());
        dto.setApprovalComment(purchaseOrder.getApprovalComment());

        List<PurchaseOrderItemDto> items = new ArrayList<>();
        if (purchaseOrder.getItems() != null) {
            purchaseOrder.getItems().forEach(item -> items.add(PurchaseOrderItemDto.from(item)));
        }
        dto.setItems(items);

        PurchaseStatus status = purchaseOrder.getStatus();
        dto.setStatus(status);
        if (status != null) {
            dto.setStatusLabel(status.getDescription());
            dto.setAllowedNextStatuses(status.allowedNextStatuses().stream().map(Enum::name).toList());
        } else {
            dto.setStatusLabel(null);
            dto.setAllowedNextStatuses(List.of());
        }
        return dto;
    }
}
