package site.hexaarch.ecommerce.logistics.domain.order.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderShippedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合根，是订单聚合的核心实体，负责管理订单行项、收货地址、付款信息和订单状态。
 *
 * @author kenyon
 */
@Getter
public class Order {
    protected List<Object> domainEvents;
    private String id;
    private String tenantId;
    private String customerId;
    private String orderNumber;
    private List<OrderItem> orderItems;
    private ShippingAddress shippingAddress;
    private PaymentInfo paymentInfo;
    private LocalDateTime createdAt;
    private OrderStatus orderStatus;
    private LocalDateTime updatedAt;

    /**
     * 受保护的无参构造函数，用于JPA和MapStruct。
     */
    protected Order() {
        this.orderItems = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
    }

    /**
     * 私有构造函数，防止直接实例化。
     *
     * @param id              订单ID
     * @param tenantId        租户ID
     * @param customerId      客户ID
     * @param orderNumber     订单编号
     * @param orderItems      订单项列表
     * @param shippingAddress 收货地址
     * @param paymentInfo     付款信息
     * @param createdAt       创建时间
     * @param orderStatus     订单状态
     * @param updatedAt       更新时间
     */
    private Order(String id, String tenantId, String customerId, String orderNumber, List<OrderItem> orderItems, ShippingAddress shippingAddress, PaymentInfo paymentInfo, LocalDateTime createdAt, OrderStatus orderStatus, LocalDateTime updatedAt) {
        this.id = id != null ? id : java.util.UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.orderNumber = orderNumber;
        this.orderItems = orderItems != null ? orderItems : new ArrayList<>();
        this.shippingAddress = shippingAddress;
        this.paymentInfo = paymentInfo;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.orderStatus = orderStatus != null ? orderStatus : OrderStatus.PENDING;
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.domainEvents = new ArrayList<>(); // 初始化domainEvents
    }

    /**
     * 创建新订单。
     *
     * @param tenantId        租户ID
     * @param customerId      客户ID
     * @param orderItems      订单行项列表
     * @param shippingAddress 收货地址
     * @param paymentInfo     付款信息
     * @return 新的订单
     */
    public static Order create(String tenantId, String customerId, List<OrderItem> orderItems, ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        Order order = new Order(
                java.util.UUID.randomUUID().toString(), // id
                tenantId, // tenantId
                customerId, // customerId
                null, // orderNumber
                orderItems, // orderItems
                shippingAddress, // shippingAddress
                paymentInfo, // paymentInfo
                LocalDateTime.now(), // createdAt
                OrderStatus.PENDING, // orderStatus
                LocalDateTime.now() // updatedAt
        );

        // 注册订单创建事件
        OrderCreatedEvent orderCreatedEvent = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .tenantId(order.getTenantId())
                .customerId(order.getCustomerId())
                .orderItems(order.getOrderItems())
                .shippingAddress(order.getShippingAddress())
                .paymentInfo(order.getPaymentInfo())
                .createdAt(order.getCreatedAt())
                .build();
        order.registerDomainEvent(orderCreatedEvent);

        return order;
    }

    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }

    protected void registerDomainEvent(Object event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    /**
     * 添加订单行项。
     *
     * @param orderItem 订单行项
     */
    public void addItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 移除订单行项。
     *
     * @param itemId 订单行项ID
     */
    public void removeItem(String itemId) {
        this.orderItems.removeIf(item -> item.getId().equals(itemId));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新订单状态。
     *
     * @param newStatus 新的订单状态
     */
    public void updateStatus(OrderStatus newStatus) {
        OrderStatus oldStatus = this.orderStatus;
        this.orderStatus = newStatus;
        this.updatedAt = LocalDateTime.now();

        // 注册订单状态变更事件
        OrderStatusChangedEvent orderStatusChangedEvent = OrderStatusChangedEvent.builder()
                .orderId(this.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedAt(LocalDateTime.now())
                .build();
        this.registerDomainEvent(orderStatusChangedEvent);

        // 如果订单状态变为已发货，注册订单发货事件
        if (newStatus.isShipped()) {
            OrderShippedEvent orderShippedEvent = OrderShippedEvent.builder()
                    .orderId(this.getId())
                    .shippedAt(LocalDateTime.now())
                    .build();
            this.registerDomainEvent(orderShippedEvent);
        }

        // 如果订单状态变为已完成，注册订单完成事件
        if (newStatus.isCompleted()) {
            OrderCompletedEvent orderCompletedEvent = new OrderCompletedEvent(
                    this.id,
                    LocalDateTime.now()
            );
            this.registerDomainEvent(orderCompletedEvent);
        }
    }

    /**
     * 计算订单总金额。
     *
     * @return 订单总金额
     */
    public BigDecimal calculateTotal() {
        return this.orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getId() {
        return id;
    }

    // 为JPA和MapStruct添加setter方法
    protected void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    protected void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    protected void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    protected void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    protected void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    protected void setShippingAddress(ShippingAddress shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    protected void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    protected void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    protected void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class OrderBuilder {
        private String id;
        private String tenantId;
        private String customerId;
        private String orderNumber;
        private List<OrderItem> orderItems;
        private ShippingAddress shippingAddress;
        private PaymentInfo paymentInfo;
        private LocalDateTime createdAt;
        private OrderStatus orderStatus;
        private LocalDateTime updatedAt;

        public OrderBuilder id(String id) {
            this.id = id;
            return this;
        }

        public OrderBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public OrderBuilder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public OrderBuilder orderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
            return this;
        }

        public OrderBuilder orderItems(List<OrderItem> orderItems) {
            this.orderItems = orderItems;
            return this;
        }

        public OrderBuilder shippingAddress(ShippingAddress shippingAddress) {
            this.shippingAddress = shippingAddress;
            return this;
        }

        public OrderBuilder paymentInfo(PaymentInfo paymentInfo) {
            this.paymentInfo = paymentInfo;
            return this;
        }

        public OrderBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OrderBuilder orderStatus(OrderStatus orderStatus) {
            this.orderStatus = orderStatus;
            return this;
        }

        public OrderBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Order build() {
            return new Order(
                    id != null ? id : java.util.UUID.randomUUID().toString(),
                    tenantId,
                    customerId,
                    orderNumber,
                    orderItems != null ? orderItems : new ArrayList<>(),
                    shippingAddress,
                    paymentInfo,
                    createdAt != null ? createdAt : java.time.LocalDateTime.now(),
                    orderStatus != null ? orderStatus : OrderStatus.PENDING,
                    updatedAt != null ? updatedAt : java.time.LocalDateTime.now()
            );
        }
    }
}