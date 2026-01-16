package site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.Location;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryCountEvent;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryMovementEvent;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 仓库聚合根，是仓储聚合的核心实体，负责管理库位和库存记录。
 *
 * @author kenyon
 */
@Getter
public class Warehouse {
    protected List<Object> domainEvents;
    private String warehouseId;
    private String tenantId;
    private String warehouseName;
    private String warehouseCode;
    private String address;
    private int capacity;
    private List<Location> locations;
    private List<InventoryRecord> inventoryRecords;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 受保护的无参构造函数，用于JPA和MapStruct。
     */
    protected Warehouse() {
        this.locations = new ArrayList<>();
        this.inventoryRecords = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
    }

    /**
     * 私有构造函数，防止直接实例化。
     *
     * @param warehouseId      仓库ID
     * @param tenantId         租户ID
     * @param warehouseName    仓库名称
     * @param warehouseCode    仓库代码
     * @param address          仓库地址
     * @param capacity         仓库容量
     * @param locations        库位列表
     * @param inventoryRecords 库存记录列表
     * @param active           是否激活
     * @param createdAt        创建时间
     * @param updatedAt        更新时间
     */
    private Warehouse(String warehouseId, String tenantId, String warehouseName, String warehouseCode, String address, int capacity, List<Location> locations, List<InventoryRecord> inventoryRecords, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.warehouseId = warehouseId != null ? warehouseId : UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.warehouseName = warehouseName;
        this.warehouseCode = warehouseCode;
        this.address = address;
        this.capacity = capacity > 0 ? capacity : 1;
        this.locations = locations != null ? locations : new ArrayList<>();
        this.inventoryRecords = inventoryRecords != null ? inventoryRecords : new ArrayList<>();
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.domainEvents = new ArrayList<>(); // 初始化domainEvents
    }

    /**
     * 创建新的仓库。
     *
     * @param tenantId      租户ID
     * @param warehouseName 仓库名称
     * @param address       仓库地址
     * @param capacity      仓库容量
     * @return 新的仓库
     */
    public static Warehouse create(String tenantId, String warehouseName, String address, int capacity) {
        return new Warehouse(
                UUID.randomUUID().toString(), // warehouseId
                tenantId, // tenantId
                warehouseName, // warehouseName
                null, // warehouseCode
                address, // address
                capacity, // capacity
                new ArrayList<>(), // locations
                new ArrayList<>(), // inventoryRecords
                true, // active
                LocalDateTime.now(), // createdAt
                LocalDateTime.now() // updatedAt
        );
    }

    public static WarehouseBuilder builder() {
        return new WarehouseBuilder();
    }

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }

    protected void registerDomainEvent(Object event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    /**
     * 添加库位。
     *
     * @param location 库位
     */
    public void addLocation(Location location) {
        this.locations.add(location);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 移除库位。
     *
     * @param locationId 库位ID
     */
    public void removeLocation(String locationId) {
        this.locations.removeIf(location -> location.getLocationId().equals(locationId));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加库存记录。
     *
     * @param inventoryRecord 库存记录
     */
    public void addInventoryRecord(InventoryRecord inventoryRecord) {
        this.inventoryRecords.add(inventoryRecord);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 查找库存记录。
     *
     * @param productId 产品ID
     * @return 库存记录，如果不存在则返回Optional.empty()
     */
    public Optional<InventoryRecord> findInventoryRecord(String productId) {
        return this.inventoryRecords.stream()
                .filter(record -> record.getProductId().equals(productId))
                .findFirst();
    }

    /**
     * 更新库存数量。
     *
     * @param productId    产品ID
     * @param quantity     变动数量
     * @param movementType 变动类型
     * @param reason       变动原因
     */
    public void updateInventory(String productId, int quantity, InventoryMovement.MovementType movementType, String reason) {
        // 查找库存记录
        InventoryRecord inventoryRecord = findInventoryRecord(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory record not found: " + productId));

        // 保存原始数量
        int originalQuantity = inventoryRecord.getQuantity();

        // 更新库存数量
        if (movementType == InventoryMovement.MovementType.INBOUND) {
            inventoryRecord.increaseQuantity(quantity);
        } else if (movementType == InventoryMovement.MovementType.OUTBOUND) {
            inventoryRecord.decreaseQuantity(quantity);
        } else if (movementType == InventoryMovement.MovementType.ADJUSTMENT) {
            inventoryRecord.adjustQuantity(quantity);
        }

        // 更新时间
        this.updatedAt = LocalDateTime.now();

        // 创建库存变动事件
        InventoryMovement movement = InventoryMovement.builder()
                .warehouseId(this.getWarehouseId())
                .productId(productId)
                .sku(inventoryRecord.getSku())
                .quantity(quantity)
                .movementType(movementType)
                .reason(reason)
                .movedAt(this.getUpdatedAt())
                .build();

        // 注册库存变动事件
        InventoryMovementEvent event = InventoryMovementEvent.builder()
                .warehouseId(this.getWarehouseId())
                .inventoryMovement(movement)
                .originalQuantity(originalQuantity)
                .newQuantity(inventoryRecord.getQuantity())
                .occurredAt(this.getUpdatedAt())
                .build();
        this.registerDomainEvent(event);

        // 如果库存状态为不足，注册库存不足事件
        if (inventoryRecord.getInventoryStatus().toString().equals("SHORTAGE") || inventoryRecord.getInventoryStatus().toString().equals("OUT_OF_STOCK")) {
            InventoryShortageEvent shortageEvent = InventoryShortageEvent.builder()
                    .warehouseId(this.getWarehouseId())
                    .productId(productId)
                    .sku(inventoryRecord.getSku())
                    .currentQuantity(inventoryRecord.getQuantity())
                    .threshold(10) // 示例阈值
                    .occurredAt(this.getUpdatedAt())
                    .build();
            this.registerDomainEvent(shortageEvent);
        }
    }

    /**
     * 库存盘点。
     *
     * @param productId      产品ID
     * @param actualQuantity 实际数量
     */
    public void countInventory(String productId, int actualQuantity) {
        // 查找库存记录
        InventoryRecord inventoryRecord = findInventoryRecord(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory record not found: " + productId));

        // 保存原始数量
        int originalQuantity = inventoryRecord.getQuantity();

        // 调整库存数量
        inventoryRecord.adjustQuantity(actualQuantity);

        // 更新时间
        this.updatedAt = LocalDateTime.now();

        // 创建盘点结果
        InventoryCountEvent.CountResult countResult = InventoryCountEvent.CountResult.builder()
                .productId(productId)
                .expectedQuantity(originalQuantity)
                .actualQuantity(actualQuantity)
                .difference(actualQuantity - originalQuantity)
                .build();

        // 注册库存盘点事件
        InventoryCountEvent event = InventoryCountEvent.builder()
                .warehouseId(this.getWarehouseId())
                .countResult(countResult)
                .countedAt(this.getUpdatedAt())
                .build();
        this.registerDomainEvent(event);
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    // 为JPA和MapStruct添加setter方法
    protected void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getTenantId() {
        return tenantId;
    }

    protected void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    protected void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    protected void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getAddress() {
        return address;
    }

    protected void setAddress(String address) {
        this.address = address;
    }

    public int getCapacity() {
        return capacity;
    }

    protected void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public List<Location> getLocations() {
        return locations;
    }

    protected void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public List<InventoryRecord> getInventoryRecords() {
        return inventoryRecords;
    }

    protected void setInventoryRecords(List<InventoryRecord> inventoryRecords) {
        this.inventoryRecords = inventoryRecords;
    }

    public boolean isActive() {
        return active;
    }

    protected void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    protected void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public WarehouseBuilder toBuilder() {
        return new WarehouseBuilder()
                .warehouseId(this.warehouseId)
                .tenantId(this.tenantId)
                .warehouseName(this.warehouseName)
                .warehouseCode(this.warehouseCode)
                .address(this.address)
                .capacity(this.capacity)
                .locations(this.locations)
                .inventoryRecords(this.inventoryRecords)
                .active(this.active)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    public static class WarehouseBuilder {
        private String warehouseId;
        private String tenantId;
        private String warehouseName;
        private String warehouseCode;
        private String address;
        private int capacity;
        private List<Location> locations;
        private List<InventoryRecord> inventoryRecords;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public WarehouseBuilder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public WarehouseBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public WarehouseBuilder warehouseName(String warehouseName) {
            this.warehouseName = warehouseName;
            return this;
        }

        public WarehouseBuilder warehouseCode(String warehouseCode) {
            this.warehouseCode = warehouseCode;
            return this;
        }

        public WarehouseBuilder address(String address) {
            this.address = address;
            return this;
        }

        public WarehouseBuilder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public WarehouseBuilder locations(List<Location> locations) {
            this.locations = locations;
            return this;
        }

        public WarehouseBuilder inventoryRecords(List<InventoryRecord> inventoryRecords) {
            this.inventoryRecords = inventoryRecords;
            return this;
        }

        public WarehouseBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public WarehouseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public WarehouseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Warehouse build() {
            Warehouse warehouse = new Warehouse(
                    warehouseId != null ? warehouseId : java.util.UUID.randomUUID().toString(),
                    tenantId,
                    warehouseName,
                    warehouseCode,
                    address,
                    capacity,
                    locations != null ? locations : new ArrayList<>(),
                    inventoryRecords != null ? inventoryRecords : new ArrayList<>(),
                    active,
                    createdAt != null ? createdAt : java.time.LocalDateTime.now(),
                    updatedAt != null ? updatedAt : java.time.LocalDateTime.now()
            );
            return warehouse;
        }
    }
}