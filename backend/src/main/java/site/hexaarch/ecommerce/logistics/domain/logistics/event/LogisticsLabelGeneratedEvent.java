package site.hexaarch.ecommerce.logistics.domain.logistics.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 物流标签生成事件，当物流标签生成时触发。
 *
 * @author kenyon
 */
@Getter
public class LogisticsLabelGeneratedEvent {
    private final String logisticsOrderId;
    private final String orderId;
    private final String labelId;
    private final String trackingNumber;
    private final LocalDateTime generatedAt;

    private LogisticsLabelGeneratedEvent(Builder builder) {
        this.logisticsOrderId = Objects.requireNonNull(builder.logisticsOrderId, "Logistics order ID cannot be null");
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.labelId = Objects.requireNonNull(builder.labelId, "Label ID cannot be null");
        this.trackingNumber = Objects.requireNonNull(builder.trackingNumber, "Tracking number cannot be null");
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "Generated at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String logisticsOrderId;
        private String orderId;
        private String labelId;
        private String trackingNumber;
        private LocalDateTime generatedAt;

        private Builder() {
        }

        public Builder logisticsOrderId(String logisticsOrderId) {
            this.logisticsOrderId = logisticsOrderId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder generatedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public LogisticsLabelGeneratedEvent build() {
            return new LogisticsLabelGeneratedEvent(this);
        }
    }
}