package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存记录JPA实体，用于持久化库存记录实体。
 *
 * @author kenyon
 */
@Entity
@Table(name = "inventory_records")
@Getter
@Setter
public class InventoryRecordJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseJpaEntity warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private LocationJpaEntity location;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InventoryStatusJpaEntity status;

    @Column(name = "last_updated")
    private Long lastUpdated;
}
