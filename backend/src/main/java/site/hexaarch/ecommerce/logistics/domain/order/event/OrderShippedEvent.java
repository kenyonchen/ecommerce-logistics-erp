package site.hexaarch.ecommerce.logistics.domain.order.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单发货事件，当订单发货时触发。
 *
 * @author kenyon
 */
@Getter
public class OrderShippedEvent {
    private final String orderId;
    private final LocalDateTime shippedAt;

    private OrderShippedEvent(Builder builder) {
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.shippedAt = Objects.requireNonNull(builder.shippedAt, "Shipped at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private LocalDateTime shippedAt;

        private Builder() {
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder shippedAt(LocalDateTime shippedAt) {
            this.shippedAt = shippedAt;
            return this;
        }

        public OrderShippedEvent build() {
            return new OrderShippedEvent(this);
        }
    }
}