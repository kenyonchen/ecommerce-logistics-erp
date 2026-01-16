package site.hexaarch.ecommerce.logistics.domain.product.event;

import lombok.Getter;

/**
 * 产品更新事件，当产品信息更新时触发。
 *
 * @author kenyon
 */
@Getter
public class ProductUpdatedEvent {
    /**
     * 产品ID
     */
    private final String productId;
    /**
     * 产品名称
     */
    private final String productName;
    /**
     * 事件发生时间戳
     */
    private final long timestamp;

    private ProductUpdatedEvent(String productId, String productName, long timestamp) {
        this.productId = productId;
        this.productName = productName;
        this.timestamp = timestamp;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String productId;
        private String productName;
        private long timestamp;

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ProductUpdatedEvent build() {
            return new ProductUpdatedEvent(productId, productName, timestamp);
        }
    }
}
