package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 店铺仓库JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "shop_warehouses",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_warehouse_platform_wh",
                columnNames = {"tenant_id", "platform", "platform_warehouse_id"}),
        indexes = {
                @Index(name = "idx_shop_warehouse_tenant", columnList = "tenant_id"),
                @Index(name = "idx_shop_warehouse_local_wh", columnList = "local_warehouse_id")
        })
@Getter
@Setter
public class ShopWarehouseJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "integration_id", length = 64)
    private String integrationId;

    @Column(name = "shop_code", length = 128)
    private String shopCode;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "platform_warehouse_id", nullable = false, length = 128)
    private String platformWarehouseId;

    @Column(name = "platform_warehouse_name", length = 255)
    private String platformWarehouseName;

    @Column(name = "local_warehouse_id", length = 64)
    private String localWarehouseId;

    @Column(name = "local_warehouse_code", length = 64)
    private String localWarehouseCode;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "default_warehouse", nullable = false)
    private boolean defaultWarehouse;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
