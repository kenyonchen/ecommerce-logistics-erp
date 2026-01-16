package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.service.OrderProcessingService;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;

/**
 * 订单应用服务，协调领域对象完成订单相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class OrderApplicationService {
    private final OrderRepository orderRepository;
    private final OrderProcessingService orderProcessingService;
    private final DomainEventPublisher domainEventPublisher;

    // 手动添加构造函数，避免Lombok注解问题
    public OrderApplicationService(OrderRepository orderRepository, OrderProcessingService orderProcessingService, DomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderProcessingService = orderProcessingService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建订单
     */
    public Order createOrder(String tenantId, String customerId, List<OrderItem> orderItems,
                             ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        var order = orderProcessingService.createOrder(tenantId, customerId, orderItems, shippingAddress, paymentInfo);
        Order savedOrder = orderRepository.save(order);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedOrder);
        return savedOrder;
    }

    /**
     * 更新订单状态
     */
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        // 更新订单状态
        order.updateStatus(newStatus);

        Order savedOrder = orderRepository.save(order);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedOrder);
        return savedOrder;
    }

    /**
     * 取消订单
     */
    public Order cancelOrder(String orderId) {
        var order = orderProcessingService.cancelOrder(orderId);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(order);
        return order;
    }

    /**
     * 查找订单
     */
    public Order findOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
    }

    public List<Order> findOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
