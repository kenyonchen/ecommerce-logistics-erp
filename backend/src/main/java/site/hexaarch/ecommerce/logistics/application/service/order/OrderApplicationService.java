package site.hexaarch.ecommerce.logistics.application.service.order;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
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
    private final WarehouseApplicationService warehouseApplicationService;
    private final LogisticsOrderRepository logisticsOrderRepository;
    private final FinanceApplicationService financeApplicationService;

    // 手动添加构造函数，避免Lombok注解问题
    public OrderApplicationService(OrderRepository orderRepository, OrderProcessingService orderProcessingService,
                                   DomainEventPublisher domainEventPublisher,
                                   WarehouseApplicationService warehouseApplicationService,
                                   LogisticsOrderRepository logisticsOrderRepository,
                                   FinanceApplicationService financeApplicationService) {
        this.orderRepository = orderRepository;
        this.orderProcessingService = orderProcessingService;
        this.domainEventPublisher = domainEventPublisher;
        this.warehouseApplicationService = warehouseApplicationService;
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.financeApplicationService = financeApplicationService;
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
     * 更新订单状态（状态机校验在聚合内完成）。
     * <p>
     * 首次变更为 SHIPPED 时按订单行逐行扣减出库仓库存（BR-04）；
     * 库存不足抛 InvalidOperationException，整个事务回滚，发货不会半途生效。
     * 状态机保证 SHIPPED 之后只能 COMPLETED，且同状态迁移不重复扣减。
     * </p>
     */
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        TenantAccessGuard.requireOwned("Order", order.getTenantId(), orderId);

        OrderStatus oldStatus = order.getOrderStatus();
        order.updateStatus(newStatus);

        if (newStatus == OrderStatus.SHIPPED && oldStatus != OrderStatus.SHIPPED) {
            deductInventoryForOrder(order);
        }

        Order savedOrder = orderRepository.save(order);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedOrder);
        return savedOrder;
    }

    /**
     * 发货扣减库存：订单行没有出库仓字段，逐行按 SKU 在租户内择仓（见
     * {@link WarehouseApplicationService#resolveOutboundWarehouseId}）；租户无仓库则业务异常阻断发货。
     */
    private void deductInventoryForOrder(Order order) {
        // 订单上没有租户时才回退，且回退到登录租户而不是 default-tenant——后者会在租户自己的仓里
        // 找不到库存，把「发货扣减」变成 404/静默不扣（ISS-118）。
        String tenantId = TenantAccessGuard.resolveWriteTenant(order.getTenantId(), DEFAULT_TENANT_ID);
        for (OrderItem item : order.getOrderItems()) {
            String warehouseId = warehouseApplicationService
                    .resolveOutboundWarehouseId(tenantId, item.getSku(), item.getQuantity());
            warehouseApplicationService.inventoryOut(item.getSku(), warehouseId, item.getQuantity());
        }
    }

    /**
     * 取消订单
     */
    @Transactional
    public Order cancelOrder(String orderId) {
        // 取消走领域服务，先按登录租户校验归属，避免跨租户改状态（ISS-050）
        findOrderById(orderId);
        var order = orderProcessingService.cancelOrder(orderId);
        // 发布订单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(order);
        return order;
    }

    /**
     * 删除订单（连同它联动生成的物流单，避免留下孤儿行）
     */
    @Transactional
    public void deleteOrder(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            TenantAccessGuard.requireOwned("Order", order.getTenantId(), orderId);
            // 下单链路会自动派生物流单，只删订单会让物流单指向一个不存在的订单：
            // 既查不到归属订单，又会让「删除租户」的业务数据守卫一直报「仍有 N 条业务数据」。
            logisticsOrderRepository.findByOrderId(orderId)
                    .forEach(logisticsOrder -> {
                        // ISS-139 / R-59：级联删物流单前要先撤它派生的运费应付，否则台账留一条
                        // 指向空单据的活账（这条路径和 DELETE /api/logistics/{id} 是同一个缺陷的两个入口）
                        financeApplicationService.reverseTransactionsForDocument(
                                logisticsOrder.getLogisticsOrderId(),
                                FinanceApplicationService.LOGISTICS_ORDER_REFERENCE_TYPE,
                                "物流单 " + logisticsOrder.getLogisticsOrderNumber());
                        logisticsOrderRepository.delete(logisticsOrder.getLogisticsOrderId());
                    });
        });
        orderRepository.delete(orderId);
    }

    /**
     * 查找订单
     */
    public Order findOrderById(String orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        TenantAccessGuard.requireOwned("Order", order.getTenantId(), orderId);
        return order;
    }

    /**
     * 按订单号查找订单
     */
    public Order findOrderByNumber(String orderNumber) {
        var order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderNumber));
        TenantAccessGuard.requireOwned("Order", order.getTenantId(), orderNumber);
        return order;
    }

    public List<Order> findOrdersByCustomerId(String customerId) {
        return withinCurrentTenant(orderRepository.findByCustomerId(customerId));
    }

    public List<Order> findOrdersByStatus(OrderStatus status) {
        return withinCurrentTenant(orderRepository.findByStatus(status));
    }

    /**
     * 列表按登录租户收敛；平台管理员或无租户上下文时不过滤（ISS-050）。
     */
    private List<Order> withinCurrentTenant(List<Order> orders) {
        String tenantId = TenantAccessGuard.currentTenantId();
        if (tenantId == null) {
            return orders;
        }
        return orders.stream()
                .filter(order -> tenantId.equals(order.getTenantId()))
                .toList();
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
        OrderQuery query = new OrderQuery(TenantAccessGuard.scopedTenantId(tenantId), orderNumber, customerId, customerName, status, platform,
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
