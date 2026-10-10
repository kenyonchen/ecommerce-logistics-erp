package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购单JPA实体，用于持久化采购单信息。
 *
 * @author kenyon
 */
@Entity
@Table(name = "purchase_orders",
        indexes = {
                @Index(name = "idx_purchase_orders_tenant", columnList = "tenant_id"),
                @Index(name = "idx_purchase_orders_status", columnList = "status"),
                @Index(name = "idx_purchase_orders_created_at", columnList = "created_at")
        })
@Getter
@Setter
public class PurchaseOrderJpaEntity {
    @Id
    @Column(name = "purchase_order_id")
    private String purchaseOrderId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "supplier_id", nullable = false)
    private String supplierId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 审批人（BR-06）：审批通过/拒绝时写入，未审批为 null
     */
    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    /**
     * 审批时间（BR-06）
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 审批意见 / 拒绝原因（BR-06）
     */
    @Column(name = "approval_comment", length = 512)
    private String approvalComment;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItemJpaEntity> items;
}
