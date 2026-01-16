package site.hexaarch.ecommerce.logistics.domain.product.event;

import lombok.Getter;

/**
 * 产品创建事件，当新产品创建时触发。
 *
 * @author kenyon
 */
@Getter
public class ProductCreatedEvent {
    /**
     * 产品ID
     */
    private final String productId;
    /**
     * 租户ID
     */
    private final String tenantId;
    /**
     * 产品名称
     */
    private final String productName;
    /**
     * 事件发生时间戳
     */
    private final long timestamp;

    private ProductCreatedEvent(String productId, String tenantId, String productName, long timestamp) {
        this.productId = productId;
        this.tenantId = tenantId;
        this.productName = productName;
        this.timestamp = timestamp;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String productId;
        private String tenantId;
        private String productName;
        private long timestamp;

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
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

        public ProductCreatedEvent build() {
            return new ProductCreatedEvent(productId, tenantId, productName, timestamp);
        }
    }
}
