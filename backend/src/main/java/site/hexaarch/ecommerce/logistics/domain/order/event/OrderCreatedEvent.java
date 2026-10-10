package site.hexaarch.ecommerce.logistics.domain.order.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 订单创建事件，当新订单创建时触发。
 *
 * @author kenyon
 */
@Getter
public class OrderCreatedEvent {
    private final String orderId;
    private final String tenantId;
    private final String customerId;
    private final List<OrderItem> orderItems;
    private final ShippingAddress shippingAddress;
    private final PaymentInfo paymentInfo;
    private final LocalDateTime createdAt;

    /**
     * MQ 消费侧的反序列化入口（ISS-052）：只有 builder 形态的私有构造时 Jackson 无 creator 可用，
     * 消费者在 `cannot convert message` 上反复重试。
     */
    @JsonCreator
    public static OrderCreatedEvent fromJson(@JsonProperty("orderId") String orderId,
                                             @JsonProperty("tenantId") String tenantId,
                                             @JsonProperty("customerId") String customerId,
                                             @JsonProperty("orderItems") List<OrderItem> orderItems,
                                             @JsonProperty("shippingAddress") ShippingAddress shippingAddress,
                                             @JsonProperty("paymentInfo") PaymentInfo paymentInfo,
                                             @JsonProperty("createdAt") LocalDateTime createdAt) {
        return builder().orderId(orderId).tenantId(tenantId).customerId(customerId).orderItems(orderItems)
                .shippingAddress(shippingAddress).paymentInfo(paymentInfo).createdAt(createdAt).build();
    }

    private OrderCreatedEvent(Builder builder) {
        this.orderId = Objects.requireNonNull(builder.orderId, "Order ID cannot be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "Tenant ID cannot be null");
        this.customerId = Objects.requireNonNull(builder.customerId, "Customer ID cannot be null");
        this.orderItems = Objects.requireNonNull(builder.orderItems, "Order items cannot be null");
        this.shippingAddress = Objects.requireNonNull(builder.shippingAddress, "Shipping address cannot be null");
        this.paymentInfo = Objects.requireNonNull(builder.paymentInfo, "Payment info cannot be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "Created at cannot be null");
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
     * 获取租户ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取客户ID
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * 获取订单行项
     */
    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    /**
     * 获取收货地址
     */
    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    /**
     * 获取付款信息
     */
    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    /**
     * 获取创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public static class Builder {
        private String orderId;
        private String tenantId;
        private String customerId;
        private List<OrderItem> orderItems;
        private ShippingAddress shippingAddress;
        private PaymentInfo paymentInfo;
        private LocalDateTime createdAt;

        private Builder() {
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder orderItems(List<OrderItem> orderItems) {
            this.orderItems = orderItems;
            return this;
        }

        public Builder shippingAddress(ShippingAddress shippingAddress) {
            this.shippingAddress = shippingAddress;
            return this;
        }

        public Builder paymentInfo(PaymentInfo paymentInfo) {
            this.paymentInfo = paymentInfo;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OrderCreatedEvent build() {
            return new OrderCreatedEvent(this);
        }
    }
}