package site.hexaarch.ecommerce.logistics.domain.order.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单状态变更事件，当订单状态发生变化时触发。
 *
 * @author kenyon
 */
@Getter
public class OrderStatusChangedEvent {
    private final String orderId;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;
    private final LocalDateTime changedAt;

    private OrderStatusChangedEvent(Builder builder) {
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.oldStatus = Objects.requireNonNull(builder.oldStatus, "Old status cannot be null");
        this.newStatus = Objects.requireNonNull(builder.newStatus, "New status cannot be null");
        this.changedAt = Objects.requireNonNull(builder.changedAt, "Changed at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取订单ID
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * 获取旧状态
     */
    public OrderStatus getOldStatus() {
        return oldStatus;
    }

    /**
     * 获取新状态
     */
    public OrderStatus getNewStatus() {
        return newStatus;
    }

    /**
     * 获取变更时间
     */
    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public static class Builder {
        private String orderId;
        private OrderStatus oldStatus;
        private OrderStatus newStatus;
        private LocalDateTime changedAt;

        private Builder() {
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder oldStatus(OrderStatus oldStatus) {
            this.oldStatus = oldStatus;
            return this;
        }

        public Builder newStatus(OrderStatus newStatus) {
            this.newStatus = newStatus;
            return this;
        }

        public Builder changedAt(LocalDateTime changedAt) {
            this.changedAt = changedAt;
            return this;
        }

        public OrderStatusChangedEvent build() {
            return new OrderStatusChangedEvent(this);
        }
    }
}