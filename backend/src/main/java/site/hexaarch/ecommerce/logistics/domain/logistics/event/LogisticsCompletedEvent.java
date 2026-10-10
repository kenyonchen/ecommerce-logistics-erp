package site.hexaarch.ecommerce.logistics.domain.logistics.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 物流完成事件，当物流完成时触发。
 *
 * @author kenyon
 */
@Getter
public class LogisticsCompletedEvent {
    private final String logisticsOrderId;
    private final String orderId;
    private final LocalDateTime completedAt;

    /**
     * MQ 消费侧的反序列化入口（ISS-052）：类只有 builder 形态的私有构造，Jackson 无 creator 可用。
     */
    @JsonCreator
    public static LogisticsCompletedEvent fromJson(@JsonProperty("logisticsOrderId") String logisticsOrderId,
                                                   @JsonProperty("orderId") String orderId,
                                                   @JsonProperty("completedAt") LocalDateTime completedAt) {
        return builder().logisticsOrderId(logisticsOrderId).orderId(orderId).completedAt(completedAt).build();
    }

    private LogisticsCompletedEvent(Builder builder) {
        this.logisticsOrderId = Objects.requireNonNull(builder.logisticsOrderId, "Logistics order ID cannot be null");
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.completedAt = Objects.requireNonNull(builder.completedAt, "Completed at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取物流单ID
     */
    public String getLogisticsOrderId() {
        return logisticsOrderId;
    }

    /**
     * 获取订单ID
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * 获取完成时间
     */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public static class Builder {
        private String logisticsOrderId;
        private String orderId;
        private LocalDateTime completedAt;

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

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public LogisticsCompletedEvent build() {
            return new LogisticsCompletedEvent(this);
        }
    }
}