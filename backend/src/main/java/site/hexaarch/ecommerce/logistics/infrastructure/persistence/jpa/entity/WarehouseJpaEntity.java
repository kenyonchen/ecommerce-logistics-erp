package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仓库JPA实体，用于持久化仓库聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
public class WarehouseJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "warehouse_name", unique = true, nullable = false)
    private String warehouseName;

    @Column(name = "warehouse_code", unique = true, nullable = false)
    private String warehouseCode;

    @Column(name = "address")
    private String address;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LocationJpaEntity> locations;

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryRecordJpaEntity> inventoryRecords;

    // Getter methods
    public String getId() {
        return id;
    }

    // Setter methods
    public void setId(String id) {
        this.id = id;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<LocationJpaEntity> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationJpaEntity> locations) {
        this.locations = locations;
    }

    public List<InventoryRecordJpaEntity> getInventoryRecords() {
        return inventoryRecords;
    }

    public void setInventoryRecords(List<InventoryRecordJpaEntity> inventoryRecords) {
        this.inventoryRecords = inventoryRecords;
    }
}
