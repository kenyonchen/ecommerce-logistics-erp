package site.hexaarch.ecommerce.logistics.domain.warehouse.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;

import java.time.LocalDateTime;

/**
 * 库存变动事件，当库存发生变动时触发。
 *
 * @author kenyon
 */
@Getter
public class InventoryMovementEvent {
    private final String warehouseId;
    private final InventoryMovement inventoryMovement;
    private final int originalQuantity;
    private final int newQuantity;
    private final LocalDateTime occurredAt;

    private InventoryMovementEvent(String warehouseId, InventoryMovement inventoryMovement, int originalQuantity, int newQuantity, LocalDateTime occurredAt) {
        this.warehouseId = warehouseId;
        this.inventoryMovement = inventoryMovement;
        this.originalQuantity = originalQuantity;
        this.newQuantity = newQuantity;
        this.occurredAt = occurredAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String warehouseId;
        private InventoryMovement inventoryMovement;
        private int originalQuantity;
        private int newQuantity;
        private LocalDateTime occurredAt;

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder inventoryMovement(InventoryMovement inventoryMovement) {
            this.inventoryMovement = inventoryMovement;
            return this;
        }

        public Builder originalQuantity(int originalQuantity) {
            this.originalQuantity = originalQuantity;
            return this;
        }

        public Builder newQuantity(int newQuantity) {
            this.newQuantity = newQuantity;
            return this;
        }

        public Builder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public InventoryMovementEvent build() {
            return new InventoryMovementEvent(warehouseId, inventoryMovement, originalQuantity, newQuantity, occurredAt);
        }
    }
}