package site.hexaarch.ecommerce.logistics.domain.warehouse.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 库存不足事件，当库存不足时触发。
 *
 * @author kenyon
 */
@Getter
public class InventoryShortageEvent {
    private final String warehouseId;
    private final String productId;
    private final String sku;
    private final int currentQuantity;
    private final int threshold;
    private final LocalDateTime occurredAt;

    private InventoryShortageEvent(String warehouseId, String productId, String sku, int currentQuantity, int threshold, LocalDateTime occurredAt) {
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.sku = sku;
        this.currentQuantity = currentQuantity;
        this.threshold = threshold;
        this.occurredAt = occurredAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取仓库ID
     */
    public String getWarehouseId() {
        return warehouseId;
    }

    /**
     * 获取产品ID
     */
    public String getProductId() {
        return productId;
    }

    /**
     * 获取SKU
     */
    public String getSku() {
        return sku;
    }

    /**
     * 获取当前数量
     */
    public int getCurrentQuantity() {
        return currentQuantity;
    }

    /**
     * 获取阈值
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * 获取发生时间
     */
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public static class Builder {
        private String warehouseId;
        private String productId;
        private String sku;
        private int currentQuantity;
        private int threshold;
        private LocalDateTime occurredAt;

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

        public Builder currentQuantity(int currentQuantity) {
            this.currentQuantity = currentQuantity;
            return this;
        }

        public Builder threshold(int threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public InventoryShortageEvent build() {
            return new InventoryShortageEvent(warehouseId, productId, sku, currentQuantity, threshold, occurredAt);
        }
    }
}