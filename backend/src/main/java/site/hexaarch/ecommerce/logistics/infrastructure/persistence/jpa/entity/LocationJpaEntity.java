package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 库位JPA实体，用于持久化库位实体。
 *
 * @author kenyon
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
public class LocationJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseJpaEntity warehouse;

    @Column(name = "location_code", nullable = false)
    private String locationCode;

    @Column(name = "location_type")
    private String locationType;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "occupied")
    private Integer occupied;

    @Column(name = "active", nullable = false)
    private boolean active;
}
