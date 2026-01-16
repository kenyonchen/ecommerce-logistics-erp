package site.hexaarch.ecommerce.logistics.domain.logistics.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 物流单创建事件，当新物流单创建时触发。
 *
 * @author kenyon
 */
@Getter
public class LogisticsOrderCreatedEvent {
    private final String logisticsOrderId;
    private final String tenantId;
    private final String orderId;
    private final String logisticsChannelId;
    private final String trackingNumber;
    private final LocalDateTime createdAt;

    private LogisticsOrderCreatedEvent(Builder builder) {
        this.logisticsOrderId = Objects.requireNonNull(builder.logisticsOrderId, "Logistics order ID cannot be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "Tenant ID cannot be null");
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.logisticsChannelId = Objects.requireNonNull(builder.logisticsChannelId, "Logistics channel ID cannot be null");
        this.trackingNumber = Objects.requireNonNull(builder.trackingNumber, "Tracking number cannot be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "Created at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String logisticsOrderId;
        private String tenantId;
        private String orderId;
        private String logisticsChannelId;
        private String trackingNumber;
        private LocalDateTime createdAt;

        private Builder() {
        }

        public Builder logisticsOrderId(String logisticsOrderId) {
            this.logisticsOrderId = logisticsOrderId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder logisticsChannelId(String logisticsChannelId) {
            this.logisticsChannelId = logisticsChannelId;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public LogisticsOrderCreatedEvent build() {
            return new LogisticsOrderCreatedEvent(this);
        }
    }
}