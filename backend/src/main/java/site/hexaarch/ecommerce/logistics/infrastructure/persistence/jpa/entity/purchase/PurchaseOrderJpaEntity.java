package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "purchase_orders")
@Getter
@Setter
public class PurchaseOrderJpaEntity {
    @Id
    @Column(name = "purchase_order_id")
    private String purchaseOrderId;

    @Column(name = "supplier_id", nullable = false)
    private String supplierId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItemJpaEntity> items;
}
