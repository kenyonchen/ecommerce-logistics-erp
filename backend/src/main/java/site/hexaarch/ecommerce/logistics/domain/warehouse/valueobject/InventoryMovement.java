package site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 库存变动值对象，记录库存的变动情况。
 *
 * @author kenyon
 */
public class InventoryMovement {
    private final String movementId;
    private final String warehouseId;
    private final String productId;
    private final String sku;
    private final int quantity;
    private final MovementType movementType;
    private final String reason;
    private final LocalDateTime movedAt;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param movementId   变动ID
     * @param warehouseId  仓库ID
     * @param productId    产品ID
     * @param sku          SKU
     * @param quantity     变动数量
     * @param movementType 变动类型
     * @param reason       变动原因
     * @param movedAt      变动时间
     */
    private InventoryMovement(String movementId, String warehouseId, String productId, String sku, int quantity, MovementType movementType, String reason, LocalDateTime movedAt) {
        this.movementId = movementId != null ? movementId : UUID.randomUUID().toString();
        this.warehouseId = Objects.requireNonNull(warehouseId, "Warehouse ID cannot be null");
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        this.sku = Objects.requireNonNull(sku, "SKU cannot be null");
        this.quantity = quantity;
        this.movementType = Objects.requireNonNull(movementType, "Movement type cannot be null");
        this.reason = reason;
        this.movedAt = movedAt != null ? movedAt : LocalDateTime.now();
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    // Getter methods
    public String getMovementId() {
        return movementId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getMovedAt() {
        return movedAt;
    }

    /**
     * 库存变动类型枚举。
     */
    public enum MovementType {
        INBOUND("inbound", "入库"),
        OUTBOUND("outbound", "出库"),
        ADJUSTMENT("adjustment", "调整"),
        TRANSFER("transfer", "调拨");

        private final String code;
        private final String description;

        MovementType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Builder {
        private String movementId;
        private String warehouseId;
        private String productId;
        private String sku;
        private int quantity;
        private MovementType movementType;
        private String reason;
        private LocalDateTime movedAt;

        public Builder movementId(String movementId) {
            this.movementId = movementId;
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

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder movementType(MovementType movementType) {
            this.movementType = movementType;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder movedAt(LocalDateTime movedAt) {
            this.movedAt = movedAt;
            return this;
        }

        public InventoryMovement build() {
            return new InventoryMovement(movementId, warehouseId, productId, sku, quantity, movementType, reason, movedAt);
        }

        public Builder toBuilder() {
            return this;
        }
    }
}