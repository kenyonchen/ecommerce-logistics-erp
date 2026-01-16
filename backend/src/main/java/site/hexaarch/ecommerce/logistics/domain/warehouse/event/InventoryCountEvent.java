package site.hexaarch.ecommerce.logistics.domain.warehouse.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 库存盘点事件，当库存盘点完成时触发。
 *
 * @author kenyon
 */
@Getter
public class InventoryCountEvent {
    private final String warehouseId;
    private final CountResult countResult;
    private final LocalDateTime countedAt;

    private InventoryCountEvent(String warehouseId, CountResult countResult, LocalDateTime countedAt) {
        this.warehouseId = warehouseId;
        this.countResult = countResult;
        this.countedAt = countedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 盘点结果值对象。
     */
    @Getter
    public static class CountResult {
        private final String productId;
        private final int expectedQuantity;
        private final int actualQuantity;
        private final int difference;

        private CountResult(String productId, int expectedQuantity, int actualQuantity, int difference) {
            this.productId = productId;
            this.expectedQuantity = expectedQuantity;
            this.actualQuantity = actualQuantity;
            this.difference = difference;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String productId;
            private int expectedQuantity;
            private int actualQuantity;
            private int difference;

            public Builder productId(String productId) {
                this.productId = productId;
                return this;
            }

            public Builder expectedQuantity(int expectedQuantity) {
                this.expectedQuantity = expectedQuantity;
                return this;
            }

            public Builder actualQuantity(int actualQuantity) {
                this.actualQuantity = actualQuantity;
                return this;
            }

            public Builder difference(int difference) {
                this.difference = difference;
                return this;
            }

            public CountResult build() {
                return new CountResult(productId, expectedQuantity, actualQuantity, difference);
            }
        }
    }

    public static class Builder {
        private String warehouseId;
        private CountResult countResult;
        private LocalDateTime countedAt;

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder countResult(CountResult countResult) {
            this.countResult = countResult;
            return this;
        }

        public Builder countedAt(LocalDateTime countedAt) {
            this.countedAt = countedAt;
            return this;
        }

        public InventoryCountEvent build() {
            return new InventoryCountEvent(warehouseId, countResult, countedAt);
        }
    }
}