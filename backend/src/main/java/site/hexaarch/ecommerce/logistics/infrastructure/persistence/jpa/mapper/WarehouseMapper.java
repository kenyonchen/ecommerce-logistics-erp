package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.Location;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.InventoryRecordJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.InventoryStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LocationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.WarehouseJpaEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仓库映射器，用于在仓库领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface WarehouseMapper {
    /**
     * 将仓库领域模型转换为JPA实体
     */
    @Mapping(source = "warehouseId", target = "id")
    @Mapping(source = "warehouseName", target = "warehouseName")
    @Mapping(source = "warehouseCode", target = "warehouseCode")
    @Mapping(source = "address", target = "address")
    @Mapping(source = "capacity", target = "capacity")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "locations", target = "locations")
    @Mapping(source = "inventoryRecords", target = "inventoryRecords")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    WarehouseJpaEntity toJpaEntity(Warehouse warehouse);

    /**
     * 将JPA实体转换为仓库领域模型
     */
    default Warehouse toDomainAggregate(WarehouseJpaEntity warehouseJpaEntity) {
        try {
            java.lang.reflect.Constructor<Warehouse> constructor = Warehouse.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Warehouse warehouse = constructor.newInstance();

            // 设置各个字段
            java.lang.reflect.Field warehouseIdField = Warehouse.class.getDeclaredField("warehouseId");
            warehouseIdField.setAccessible(true);
            warehouseIdField.set(warehouse, warehouseJpaEntity.getId());

            java.lang.reflect.Field tenantIdField = Warehouse.class.getDeclaredField("tenantId");
            tenantIdField.setAccessible(true);
            tenantIdField.set(warehouse, "default-tenant"); // 暂时设置默认租户ID，实际应用中应该从JPA实体中获取

            java.lang.reflect.Field warehouseNameField = Warehouse.class.getDeclaredField("warehouseName");
            warehouseNameField.setAccessible(true);
            warehouseNameField.set(warehouse, warehouseJpaEntity.getWarehouseName());

            java.lang.reflect.Field warehouseCodeField = Warehouse.class.getDeclaredField("warehouseCode");
            warehouseCodeField.setAccessible(true);
            warehouseCodeField.set(warehouse, warehouseJpaEntity.getWarehouseCode());

            java.lang.reflect.Field addressField = Warehouse.class.getDeclaredField("address");
            addressField.setAccessible(true);
            addressField.set(warehouse, warehouseJpaEntity.getAddress());

            java.lang.reflect.Field capacityField = Warehouse.class.getDeclaredField("capacity");
            capacityField.setAccessible(true);
            capacityField.set(warehouse, warehouseJpaEntity.getCapacity());

            java.lang.reflect.Field activeField = Warehouse.class.getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(warehouse, warehouseJpaEntity.isActive());

            java.lang.reflect.Field locationsField = Warehouse.class.getDeclaredField("locations");
            locationsField.setAccessible(true);
            locationsField.set(warehouse, toLocationDomainEntityList(warehouseJpaEntity.getLocations()));

            java.lang.reflect.Field inventoryRecordsField = Warehouse.class.getDeclaredField("inventoryRecords");
            inventoryRecordsField.setAccessible(true);
            inventoryRecordsField.set(warehouse, toInventoryRecordDomainEntityList(warehouseJpaEntity.getInventoryRecords()));

            java.lang.reflect.Field createdAtField = Warehouse.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(warehouse, warehouseJpaEntity.getCreatedAt());

            java.lang.reflect.Field updatedAtField = Warehouse.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(warehouse, warehouseJpaEntity.getUpdatedAt());

            // 初始化domainEvents字段
            java.lang.reflect.Field domainEventsField = Warehouse.class.getDeclaredField("domainEvents");
            domainEventsField.setAccessible(true);
            domainEventsField.set(warehouse, new java.util.ArrayList<>());

            return warehouse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Warehouse from JPA entity", e);
        }
    }

    /**
     * 将库位领域实体转换为JPA实体
     */
    @Mapping(source = "locationId", target = "id")
    @Mapping(source = "warehouseId", target = "warehouse.id")
    @Mapping(source = "locationCode", target = "locationCode")
    @Mapping(source = "locationType", target = "locationType")
    @Mapping(source = "capacity", target = "capacity")
    @Mapping(source = "occupied", target = "occupied")
    @Mapping(source = "active", target = "active")
    LocationJpaEntity toJpaEntity(Location location);

    /**
     * 将库位JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "locationId")
    @Mapping(source = "warehouse.id", target = "warehouseId")
    @Mapping(source = "locationCode", target = "locationCode")
    @Mapping(source = "locationType", target = "locationType")
    @Mapping(source = "capacity", target = "capacity")
    @Mapping(source = "occupied", target = "occupied")
    @Mapping(source = "active", target = "active")
    Location toDomainEntity(LocationJpaEntity locationJpaEntity);

    /**
     * 将库存记录领域实体转换为JPA实体
     */
    @Mapping(source = "inventoryId", target = "id")
    @Mapping(source = "sku", target = "skuCode")
    @Mapping(source = "warehouseId", target = "warehouse.id")
    @Mapping(source = "locationId", target = "location.id")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "inventoryStatus", target = "status", qualifiedByName = "inventoryStatusToJpaEntity")
    @Mapping(source = "lastUpdated", target = "lastUpdated", qualifiedByName = "localDateTimeToLong")
    InventoryRecordJpaEntity toJpaEntity(InventoryRecord inventoryRecord);

    /**
     * 将库存记录JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "inventoryId")
    @Mapping(source = "skuCode", target = "sku")
    @Mapping(source = "warehouse.id", target = "warehouseId")
    @Mapping(source = "location.id", target = "locationId")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "status", target = "inventoryStatus", qualifiedByName = "inventoryStatusFromJpaEntity")
    @Mapping(source = "lastUpdated", target = "lastUpdated", qualifiedByName = "longToLocalDateTime")
    InventoryRecord toDomainEntity(InventoryRecordJpaEntity inventoryRecordJpaEntity);

    /**
     * 将库存状态转换为JPA实体枚举
     */
    @Named("inventoryStatusToJpaEntity")
    default InventoryStatusJpaEntity inventoryStatusToJpaEntity(InventoryStatus status) {
        return switch (status) {
            case NORMAL -> InventoryStatusJpaEntity.NORMAL;
            case SHORTAGE -> InventoryStatusJpaEntity.SHORTAGE;
            case EXCESS -> InventoryStatusJpaEntity.EXCESS;
            case OUT_OF_STOCK -> InventoryStatusJpaEntity.OUT_OF_STOCK;
        };
    }

    /**
     * 将JPA实体枚举转换为库存状态
     */
    @Named("inventoryStatusFromJpaEntity")
    default InventoryStatus inventoryStatusFromJpaEntity(InventoryStatusJpaEntity status) {
        return switch (status) {
            case NORMAL -> InventoryStatus.NORMAL;
            case SHORTAGE -> InventoryStatus.SHORTAGE;
            case EXCESS -> InventoryStatus.EXCESS;
            case OUT_OF_STOCK -> InventoryStatus.OUT_OF_STOCK;
        };
    }

    @Named("localDateTimeToLong")
    default Long localDateTimeToLong(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }

    @Named("longToLocalDateTime")
    default LocalDateTime longToLocalDateTime(Long timestamp) {
        return timestamp != null ? java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null;
    }

    /**
     * 将库位列表转换为JPA实体列表
     */
    List<LocationJpaEntity> toLocationJpaEntityList(List<Location> locations);

    /**
     * 将库位JPA实体列表转换为领域实体列表
     */
    List<Location> toLocationDomainEntityList(List<LocationJpaEntity> locationJpaEntities);

    /**
     * 将库存记录列表转换为JPA实体列表
     */
    List<InventoryRecordJpaEntity> toInventoryRecordJpaEntityList(List<InventoryRecord> inventoryRecords);

    /**
     * 将库存记录JPA实体列表转换为领域实体列表
     */
    List<InventoryRecord> toInventoryRecordDomainEntityList(List<InventoryRecordJpaEntity> inventoryRecordJpaEntities);
}
