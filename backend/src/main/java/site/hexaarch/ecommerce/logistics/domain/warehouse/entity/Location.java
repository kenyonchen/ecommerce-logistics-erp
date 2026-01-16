package site.hexaarch.ecommerce.logistics.domain.warehouse.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 库位实体，表示仓库中的具体位置，用于存放商品和管理库存。
 *
 * @author kenyon
 */
@Getter
@Builder
public class Location {
    private final String locationId;
    private final String warehouseId;
    private final String locationCode;
    private final String locationType;
    private final int capacity;
    private final int occupied;
    private final boolean active;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param locationId   库位ID
     * @param warehouseId  仓库ID
     * @param locationCode 库位代码
     * @param locationType 库位类型
     * @param capacity     库位容量
     * @param occupied     已占用容量
     * @param active       是否激活
     */
    public Location(String locationId, String warehouseId, String locationCode, String locationType, int capacity, int occupied, boolean active) {
        this.locationId = locationId != null ? locationId : UUID.randomUUID().toString();
        this.warehouseId = warehouseId;
        this.locationCode = locationCode;
        this.locationType = locationType;
        this.capacity = capacity > 0 ? capacity : 1;
        this.occupied = occupied >= 0 ? occupied : 0;
        this.active = active;
    }

    /**
     * 激活库位。
     *
     * @return 激活后的库位
     */
    public Location activate() {
        return new Location(
                this.locationId,
                this.warehouseId,
                this.locationCode,
                this.locationType,
                this.capacity,
                this.occupied,
                true
        );
    }

    /**
     * 停用库位。
     *
     * @return 停用后的库位
     */
    public Location deactivate() {
        return new Location(
                this.locationId,
                this.warehouseId,
                this.locationCode,
                this.locationType,
                this.capacity,
                this.occupied,
                false
        );
    }

    /**
     * 检查库位是否可用。
     *
     * @return 如果库位可用则返回true，否则返回false
     */
    public boolean isAvailable() {
        return this.active && this.occupied < this.capacity;
    }

    /**
     * 计算库位可用容量。
     *
     * @return 库位可用容量
     */
    public int getAvailableCapacity() {
        return this.capacity - this.occupied;
    }

    /**
     * 获取库位ID。
     *
     * @return 库位ID
     */
    public String getLocationId() {
        return locationId;
    }
}