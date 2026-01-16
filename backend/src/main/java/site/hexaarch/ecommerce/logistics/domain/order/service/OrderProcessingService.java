package site.hexaarch.ecommerce.logistics.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单处理服务，负责处理订单的创建、修改、取消等操作。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class OrderProcessingService {
    private final OrderRepository orderRepository;

    /**
     * 创建订单。
     *
     * @param tenantId        租户ID
     * @param customerId      客户ID
     * @param orderItems      订单行项列表
     * @param shippingAddress 收货地址
     * @param paymentInfo     付款信息
     * @return 创建的订单
     */
    public Order createOrder(String tenantId, String customerId, List<OrderItem> orderItems, ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        // 业务逻辑：验证订单信息
        validateOrderInfo(customerId, orderItems, shippingAddress, paymentInfo);

        // 创建订单
        Order order = Order.create(tenantId, customerId, orderItems, shippingAddress, paymentInfo);

        // 保存订单
        return orderRepository.save(order);
    }

    /**
     * 处理订单。
     *
     * @param orderId 订单ID
     * @return 处理后的订单
     */
    public Order processOrder(String orderId) {
        // 查找订单
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // 更新订单状态为处理中
        order.updateStatus(OrderStatus.PROCESSING);

        // 保存订单
        return orderRepository.save(order);
    }

    /**
     * 取消订单。
     *
     * @param orderId 订单ID
     * @return 取消后的订单
     */
    public Order cancelOrder(String orderId) {
        // 查找订单
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // 检查订单状态是否可以取消
        if (order.getOrderStatus().isShipped() || order.getOrderStatus().isCompleted()) {
            throw new IllegalStateException("Cannot cancel order that has been shipped or completed");
        }

        // 更新订单状态为已取消
        order.updateStatus(OrderStatus.CANCELLED);

        // 保存订单
        return orderRepository.save(order);
    }

    /**
     * 发货订单。
     *
     * @param orderId 订单ID
     * @return 发货后的订单
     */
    public Order shipOrder(String orderId) {
        // 查找订单
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // 检查订单状态是否可以发货
        if (!order.getOrderStatus().isProcessing()) {
            throw new IllegalStateException("Cannot ship order that is not in processing status");
        }

        // 更新订单状态为已发货
        order.updateStatus(OrderStatus.SHIPPED);

        // 保存订单
        return orderRepository.save(order);
    }

    /**
     * 完成订单。
     *
     * @param orderId 订单ID
     * @return 完成后的订单
     */
    public Order completeOrder(String orderId) {
        // 查找订单
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // 检查订单状态是否可以完成
        if (!order.getOrderStatus().isShipped()) {
            throw new IllegalStateException("Cannot complete order that has not been shipped");
        }

        // 更新订单状态为已完成
        order.updateStatus(OrderStatus.COMPLETED);

        // 保存订单
        return orderRepository.save(order);
    }

    /**
     * 验证订单信息。
     *
     * @param customerId      客户ID
     * @param orderItems      订单行项列表
     * @param shippingAddress 收货地址
     * @param paymentInfo     付款信息
     */
    private void validateOrderInfo(String customerId, List<OrderItem> orderItems, ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        // 验证客户ID
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }

        // 验证订单行项
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be null or empty");
        }

        // 验证收货地址
        if (shippingAddress == null) {
            throw new IllegalArgumentException("Shipping address cannot be null");
        }

        // 验证付款信息
        if (paymentInfo == null) {
            throw new IllegalArgumentException("Payment info cannot be null");
        }

        // 验证订单金额
        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(paymentInfo.getAmount()) != 0) {
            throw new IllegalArgumentException("Order total amount does not match payment amount");
        }
    }
}