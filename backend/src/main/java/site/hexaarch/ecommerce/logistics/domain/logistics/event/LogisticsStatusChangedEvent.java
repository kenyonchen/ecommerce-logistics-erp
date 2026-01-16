package site.hexaarch.ecommerce.logistics.domain.logistics.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 物流状态变更事件，当物流状态发生变化时触发。
 *
 * @author kenyon
 */
@Getter
public class LogisticsStatusChangedEvent {
    private final String logisticsOrderId;
    private final String orderId;
    private final LogisticsStatus oldStatus;
    private final LogisticsStatus newStatus;
    private final LocalDateTime changedAt;

    private LogisticsStatusChangedEvent(Builder builder) {
        this.logisticsOrderId = Objects.requireNonNull(builder.logisticsOrderId, "Logistics order ID cannot be null");
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.oldStatus = Objects.requireNonNull(builder.oldStatus, "Old status cannot be null");
        this.newStatus = Objects.requireNonNull(builder.newStatus, "New status cannot be null");
        this.changedAt = Objects.requireNonNull(builder.changedAt, "Changed at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String logisticsOrderId;
        private String orderId;
        private LogisticsStatus oldStatus;
        private LogisticsStatus newStatus;
        private LocalDateTime changedAt;

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

        public Builder oldStatus(LogisticsStatus oldStatus) {
            this.oldStatus = oldStatus;
            return this;
        }

        public Builder newStatus(LogisticsStatus newStatus) {
            this.newStatus = newStatus;
            return this;
        }

        public Builder changedAt(LocalDateTime changedAt) {
            this.changedAt = changedAt;
            return this;
        }

        public LogisticsStatusChangedEvent build() {
            return new LogisticsStatusChangedEvent(this);
        }
    }
}