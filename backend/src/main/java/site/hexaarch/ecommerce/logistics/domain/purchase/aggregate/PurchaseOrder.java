package site.hexaarch.ecommerce.logistics.domain.purchase.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
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
    private final LocalDateTime createdAt;
    private final List<PurchaseOrderItem> items;
    private PurchaseStatus status;
    private LocalDateTime updatedAt;
    private BigDecimal totalAmount;

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
     *
     * @param skuCode     SKU编码
     * @param productName 产品名称
     * @param quantity    数量
     * @param unitPrice   单价
     */
    public void addItem(String skuCode, String productName, Integer quantity, BigDecimal unitPrice) {
        var item = PurchaseOrderItem.create(skuCode, productName, quantity, unitPrice);
        this.items.add(item);
        // 更新总金额
        this.totalAmount = this.totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 批准采购单。
     */
    public void approve() {
        if (this.status != PurchaseStatus.PENDING) {
            throw new IllegalArgumentException("采购单状态不是待处理，无法批准");
        }
        this.status = PurchaseStatus.APPROVED;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                PurchaseStatus.PENDING,
                PurchaseStatus.APPROVED,
                this.updatedAt
        ));
    }

    /**
     * 拒绝采购单。
     */
    public void reject() {
        if (this.status != PurchaseStatus.PENDING) {
            throw new IllegalArgumentException("采购单状态不是待处理，无法拒绝");
        }
        this.status = PurchaseStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                PurchaseStatus.PENDING,
                PurchaseStatus.REJECTED,
                this.updatedAt
        ));
    }

    /**
     * 标记采购单为运输中。
     */
    public void markAsInTransit() {
        if (this.status != PurchaseStatus.APPROVED) {
            throw new IllegalArgumentException("采购单状态不是已批准，无法标记为运输中");
        }
        this.status = PurchaseStatus.IN_TRANSIT;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                PurchaseStatus.APPROVED,
                PurchaseStatus.IN_TRANSIT,
                this.updatedAt
        ));
    }

    /**
     * 标记采购单为已送达。
     */
    public void markAsDelivered() {
        if (this.status != PurchaseStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("采购单状态不是运输中，无法标记为已送达");
        }
        this.status = PurchaseStatus.DELIVERED;
        this.updatedAt = LocalDateTime.now();
        // 发布采购单状态变更事件
        this.registerDomainEvent(new PurchaseOrderStatusChangedEvent(
                this.purchaseOrderId,
                PurchaseStatus.IN_TRANSIT,
                PurchaseStatus.DELIVERED,
                this.updatedAt
        ));
    }

    /**
     * 取消采购单。
     */
    public void cancel() {
        if (this.status == PurchaseStatus.DELIVERED) {
            throw new IllegalArgumentException("采购单已送达，无法取消");
        }
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
                .totalAmount(this.totalAmount);
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

        public PurchaseOrder build() {
            var purchaseOrder = new PurchaseOrder(
                    this.purchaseOrderId,
                    this.tenantId,
                    this.supplierId,
                    this.supplierName,
                    this.warehouseId
            );
            purchaseOrder.status = this.status != null ? this.status : PurchaseStatus.PENDING;
            purchaseOrder.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            purchaseOrder.items.addAll(this.items != null ? this.items : new ArrayList<>());
            purchaseOrder.totalAmount = this.totalAmount != null ? this.totalAmount : BigDecimal.ZERO;
            return purchaseOrder;
        }
    }
}