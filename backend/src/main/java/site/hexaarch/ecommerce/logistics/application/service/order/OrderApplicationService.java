package site.hexaarch.ecommerce.logistics.application.service.order;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderQuery;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.service.OrderProcessingService;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单应用服务，协调领域对象完成订单相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class OrderApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";

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
    @Transactional
    public Order createOrder(String tenantId, String customerId, List<OrderItem> orderItems,
                             ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        return createOrder(tenantId, null, customerId, null, null, orderItems, shippingAddress, paymentInfo);
    }

    /**
     * 创建订单（完整参数，支持外部传入订单号、客户名称/邮箱）
     */
    @Transactional
    public Order createOrder(String tenantId, String orderNumber, String customerId, String customerName,
                             String customerEmail, List<OrderItem> orderItems,
                             ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        var order = orderProcessingService.createOrder(tenantId, orderNumber, customerId, customerName,
                customerEmail, orderItems, shippingAddress, paymentInfo);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(order);
        return order;
    }

    /**
     * 更新订单状态（状态机校验在聚合内完成）
     */
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        order.updateStatus(newStatus);

        Order savedOrder = orderRepository.save(order);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedOrder);
        return savedOrder;
    }

    /**
     * 取消订单
     */
    @Transactional
    public Order cancelOrder(String orderId) {
        var order = orderProcessingService.cancelOrder(orderId);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(order);
        return order;
    }

    /**
     * 删除订单
     */
    @Transactional
    public void deleteOrder(String orderId) {
        if (orderRepository.findById(orderId).isEmpty()) {
            throw new EntityNotFoundException("Order", orderId);
        }
        orderRepository.delete(orderId);
    }

    /**
     * 查找订单
     */
    public Order findOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
    }

    /**
     * 按订单号查找订单
     */
    public Order findOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderNumber));
    }

    public List<Order> findOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> findOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    /**
     * 分页 + 条件查询订单。
     *
     * @param tenantId     租户ID
     * @param orderNumber  订单号（模糊）
     * @param customerId   客户ID
     * @param customerName 客户名称（模糊）
     * @param status       订单状态
     * @param platform     来源平台
     * @param keyword      关键字
     * @param startDate    创建时间起（yyyy-MM-dd）
     * @param endDate      创建时间止（yyyy-MM-dd）
     * @param page         页码，从0开始
     * @param size         每页条数
     */
    public PageResult<Order> searchOrders(String tenantId, String orderNumber, String customerId,
                                          String customerName, OrderStatus status, String platform,
                                          String keyword, String startDate, String endDate,
                                          int page, int size) {
        OrderQuery query = new OrderQuery(tenantId, orderNumber, customerId, customerName, status, platform,
                null, keyword, parseStartDate(startDate), parseEndDate(endDate));
        return orderRepository.search(query, page, size);
    }

    private LocalDateTime parseStartDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "开始日期格式不正确（应为 yyyy-MM-dd）: " + text);
        }
    }

    private LocalDateTime parseEndDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.of(LocalDate.parse(text.trim()), LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "结束日期格式不正确（应为 yyyy-MM-dd）: " + text);
        }
    }
}
