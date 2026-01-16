package site.hexaarch.ecommerce.logistics.domain.product.event;

import lombok.Getter;

/**
 * SKU创建事件，当新SKU创建时触发。
 *
 * @author kenyon
 */
@Getter
public class SKUCreatedEvent {
    /**
     * SKU ID
     */
    private final String skuId;
    /**
     * SKU编码
     */
    private final String skuCode;
    /**
     * 产品ID
     */
    private final String productId;
    /**
     * 事件发生时间戳
     */
    private final long timestamp;

    private SKUCreatedEvent(String skuId, String skuCode, String productId, long timestamp) {
        this.skuId = skuId;
        this.skuCode = skuCode;
        this.productId = productId;
        this.timestamp = timestamp;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String skuId;
        private String skuCode;
        private String productId;
        private long timestamp;

        public Builder skuId(String skuId) {
            this.skuId = skuId;
            return this;
        }

        public Builder skuCode(String skuCode) {
            this.skuCode = skuCode;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SKUCreatedEvent build() {
            return new SKUCreatedEvent(skuId, skuCode, productId, timestamp);
        }
    }
}
