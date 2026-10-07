package site.hexaarch.ecommerce.logistics.domain.purchase.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.event.PurchaseOrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.purchase.event.PurchaseOrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购单聚合根，负责管理采购单的基本信息、采购项和状态变更。
 *
 * @author kenyon
 */
@Getter
public class PurchaseOrder extends BaseAggregateRoot {
    private final String purchaseOrderId;
    private final String tenantId;
    private final String supplierId;
    private final String supplierName;
    private final String warehouseId;
    private LocalDateTime createdAt;
    private final List<PurchaseOrderItem> items;
    private PurchaseStatus status;
    private LocalDateTime updatedAt;
    private BigDecimal totalAmount;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalComment;

    /**
     * 私有构造函数，只能通过Builder创建。
     */
    private PurchaseOrder(String purchaseOrderId, String tenantId, String supplierId, String supplierName, String warehouseId) {
        this.purchaseOrderId = purchaseOrderId;
        this.tenantId = tenantId;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.warehouseId = warehouseId;
        this.status = PurchaseStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.items = new ArrayList<>();
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * 创建采购单。
     *
     * @param purchaseOrderId 采购单号
     * @param tenantId        租户ID
     * @param supplierId      供应商ID
     * @param supplierName    供应商名称
     * @param warehouseId     仓库ID
     * @return 采购单实例
     */
    public static PurchaseOrder create(String purchaseOrderId, String tenantId, String supplierId, String supplierName, String warehouseId) {
        var purchaseOrder = new PurchaseOrder(purchaseOrderId, tenantId, supplierId, supplierName, warehouseId);
        // 发布采购单创建事件
        purchaseOrder.registerDomainEvent(new PurchaseOrderCreatedEvent(
                purchaseOrder.purchaseOrderId,
                purchaseOrder.tenantId,
                purchaseOrder.supplierId,
                purchaseOrder.warehouseId,
                purchaseOrder.createdAt
        ));
        return purchaseOrder;
    }

    // 手动添加builder方法
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 添加采购项。
     * <p>
     * 同一张采购单内 SKU 不允许重复（{@code purchase_order_items} 上没有 uk_po_item 唯一键，
     * 只能在聚合侧拦，见 docs/db/data-dictionary.md R-05）。
     * </p>
     *
     * @param skuCode     SKU编码
     * @param productName 产品名称
     * @param quantity    数量
     * @param unitPrice   单价
     */
    public void addItem(String skuCode, String productName, Integer quantity, BigDecimal unitPrice) {
        requireEditable();
        var item = PurchaseOrderItem.create(skuCode, productName, quantity, unitPrice);
        boolean duplicated = this.items.stream()
                .anyMatch(existing -> existing.getSkuCode().equalsIgnoreCase(item.getSkuCode()));
        if (duplicated) {
            throw new BusinessException("采购单内已存在 SKU：" + item.getSkuCode() + "，请直接修改该明细行", "DUPLICATE_PURCHASE_ITEM");
        }
        this.items.add(item);
        recalculateTotalAmount();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 修改采购项的数量与单价。
     *
     * @param itemId    采购项ID
     * @param quantity  新数量
     * @param unitPrice 新单价
     */
    public void updateItem(String itemId, Integer quantity, BigDecimal unitPrice) {
        requireEditable();
        var index = indexOfItem(itemId);
        var updated = this.items.get(index).updatedCopy(quantity, unitPrice);
        this.items.set(index, updated);
        recalculateTotalAmount();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 删除采购项。
     *
     * @param itemId 采购项ID
     */
    public void removeItem(String itemId) {
        requireEditable();
        this.items.remove(this.items.get(indexOfItem(itemId)));
        recalculateTotalAmount();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 批准采购单。
     *
     * @param approvedBy 审批人
     * @param comment    审批意见
     */
    public void approve(String approvedBy, String comment) {
        this.status.requireTransitionTo(PurchaseStatus.APPROVED);
        var oldStatus = this.status;
        this.status = PurchaseStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.approvalComment = comment;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                oldStatus,
                PurchaseStatus.APPROVED,
                this.updatedAt
        ));
    }

    /**
     * 拒绝采购单。
     *
     * @param approvedBy 审批人
     * @param reason     拒绝原因
     */
    public void reject(String approvedBy, String reason) {
        this.status.requireTransitionTo(PurchaseStatus.REJECTED);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("拒绝采购单必须填写拒绝原因");
        }
        var oldStatus = this.status;
        this.status = PurchaseStatus.REJECTED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.approvalComment = reason;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                oldStatus,
                PurchaseStatus.REJECTED,
                this.updatedAt
        ));
    }

    /**
     * 标记采购单为运输中。
     */
    public void markAsInTransit() {
        this.status.requireTransitionTo(PurchaseStatus.IN_TRANSIT);
        var oldStatus = this.status;
        this.status = PurchaseStatus.IN_TRANSIT;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                oldStatus,
                PurchaseStatus.IN_TRANSIT,
                this.updatedAt
        ));
    }

    /**
     * 标记采购单为已送达。
     */
    public void markAsDelivered() {
        this.status.requireTransitionTo(PurchaseStatus.DELIVERED);
        var oldStatus = this.status;
        this.status = PurchaseStatus.DELIVERED;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                oldStatus,
                PurchaseStatus.DELIVERED,
                this.updatedAt
        ));
    }

    /**
     * 取消采购单。
     * <p>
     * 只有 PENDING / APPROVED 可取消：已发货（IN_TRANSIT）需走退货流程，终态不可回退。
     * </p>
     */
    public void cancel() {
        this.status.requireTransitionTo(PurchaseStatus.CANCELLED);
        var oldStatus = this.status;
        this.status = PurchaseStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                oldStatus,
                PurchaseStatus.CANCELLED,
                this.updatedAt
        ));
    }

    /**
     * 明细行只在待处理（PENDING）阶段可维护：审批通过后金额已进入审批与应付口径，
     * 前端按钮门禁（PurchaseDetail.jsx）此前是唯一防线，服务端补齐后不可绕过。
     */
    private void requireEditable() {
        if (this.status != PurchaseStatus.PENDING) {
            throw new BusinessException(
                    String.format("采购单当前状态为「%s」，明细行只能在「待处理」阶段维护", this.status.getDescription()),
                    "PURCHASE_ITEMS_LOCKED");
        }
    }

    private int indexOfItem(String itemId) {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).getId() != null && this.items.get(i).getId().equals(itemId)) {
                return i;
            }
        }
        throw new EntityNotFoundException("采购项不存在: " + itemId);
    }

    /**
     * 总金额恒等于明细小计之和，避免addItem累加与改删行后的口径漂移。
     */
    private void recalculateTotalAmount() {
        this.totalAmount = this.items.stream()
                .map(PurchaseOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 手动添加toBuilder方法
    public Builder toBuilder() {
        return new Builder()
                .purchaseOrderId(this.purchaseOrderId)
                .tenantId(this.tenantId)
                .supplierId(this.supplierId)
                .supplierName(this.supplierName)
                .warehouseId(this.warehouseId)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .items(this.items)
                .totalAmount(this.totalAmount)
                .approvedBy(this.approvedBy)
                .approvedAt(this.approvedAt)
                .approvalComment(this.approvalComment);
    }

    public static class Builder {
        private String purchaseOrderId;
        private String tenantId;
        private String supplierId;
        private String supplierName;
        private String warehouseId;
        private PurchaseStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<PurchaseOrderItem> items;
        private BigDecimal totalAmount;
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approvalComment;

        public Builder purchaseOrderId(String purchaseOrderId) {
            this.purchaseOrderId = purchaseOrderId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder supplierId(String supplierId) {
            this.supplierId = supplierId;
            return this;
        }

        public Builder supplierName(String supplierName) {
            this.supplierName = supplierName;
            return this;
        }

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder status(PurchaseStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder items(List<PurchaseOrderItem> items) {
            this.items = items;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder approvedAt(LocalDateTime approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public Builder approvalComment(String approvalComment) {
            this.approvalComment = approvalComment;
            return this;
        }

        public PurchaseOrder build() {
            var purchaseOrder = new PurchaseOrder(
                    this.purchaseOrderId,
                    this.tenantId,
                    this.supplierId,
                    this.supplierName,
                    this.warehouseId
            );
            purchaseOrder.status = this.status != null ? this.status : PurchaseStatus.PENDING;
            purchaseOrder.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            purchaseOrder.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            purchaseOrder.items.addAll(this.items != null ? this.items : new ArrayList<>());
            purchaseOrder.totalAmount = this.totalAmount != null ? this.totalAmount : BigDecimal.ZERO;
            purchaseOrder.approvedBy = this.approvedBy;
            purchaseOrder.approvedAt = this.approvedAt;
            purchaseOrder.approvalComment = this.approvalComment;
            return purchaseOrder;
        }
    }
}
