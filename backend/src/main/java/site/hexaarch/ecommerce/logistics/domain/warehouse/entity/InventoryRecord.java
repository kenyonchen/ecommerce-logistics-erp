package site.hexaarch.ecommerce.logistics.domain.warehouse.entity;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;

import java.time.LocalDateTime;

/**
 * 库存记录实体，表示商品在仓库中的库存情况。
 *
 * @author kenyon
 */
@Getter
public class InventoryRecord {
    private final String inventoryId;
    private final String warehouseId;
    private final String productId;
    private final String sku;
    private final String locationId;
    private final LocalDateTime lastUpdated;
    private int quantity;
    private InventoryStatus inventoryStatus;

    private InventoryRecord(String inventoryId, String warehouseId, String productId, String sku, String locationId, LocalDateTime lastUpdated, int quantity, InventoryStatus inventoryStatus) {
        this.inventoryId = inventoryId;
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.sku = sku;
        this.locationId = locationId;
        this.lastUpdated = lastUpdated;
        this.quantity = quantity;
        this.inventoryStatus = inventoryStatus;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 计算库存状态。
     *
     * @param quantity 数量
     * @return 库存状态
     */
    private InventoryStatus calculateInventoryStatus(int quantity) {
        if (quantity <= 0) {
            return InventoryStatus.OUT_OF_STOCK;
        } else if (quantity < 10) {
            return InventoryStatus.SHORTAGE;
        } else if (quantity > 100) {
            return InventoryStatus.EXCESS;
        } else {
            return InventoryStatus.NORMAL;
        }
    }

    /**
     * 增加库存。
     *
     * @param amount 增加的数量
     */
    public void increaseQuantity(int amount) {
        if (amount > 0) {
            this.quantity += amount;
            this.inventoryStatus = calculateInventoryStatus(this.quantity);
        }
    }

    /**
     * 减少库存。
     *
     * @param amount 减少的数量
     */
    public void decreaseQuantity(int amount) {
        if (amount > 0) {
            this.quantity = Math.max(0, this.quantity - amount);
            this.inventoryStatus = calculateInventoryStatus(this.quantity);
        }
    }

    /**
     * 调整库存。
     *
     * @param newQuantity 新的数量
     */
    public void adjustQuantity(int newQuantity) {
        this.quantity = Math.max(0, newQuantity);
        this.inventoryStatus = calculateInventoryStatus(this.quantity);
    }

    /**
     * 获取产品ID。
     *
     * @return 产品ID
     */
    public String getProductId() {
        return productId;
    }

    /**
     * 获取数量。
     *
     * @return 数量
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * 获取SKU。
     *
     * @return SKU
     */
    public String getSku() {
        return sku;
    }

    /**
     * 获取库存状态。
     *
     * @return 库存状态
     */
    public InventoryStatus getInventoryStatus() {
        return inventoryStatus;
    }

    public static class Builder {
        private String inventoryId;
        private String warehouseId;
        private String productId;
        private String sku;
        private String locationId;
        private LocalDateTime lastUpdated;
        private int quantity;
        private InventoryStatus inventoryStatus;

        public Builder inventoryId(String inventoryId) {
            this.inventoryId = inventoryId;
            return this;
        }

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder locationId(String locationId) {
            this.locationId = locationId;
            return this;
        }

        public Builder lastUpdated(LocalDateTime lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder inventoryStatus(InventoryStatus inventoryStatus) {
            this.inventoryStatus = inventoryStatus;
            return this;
        }

        public InventoryRecord build() {
            return new InventoryRecord(inventoryId, warehouseId, productId, sku, locationId, lastUpdated, quantity, inventoryStatus);
        }
    }
}