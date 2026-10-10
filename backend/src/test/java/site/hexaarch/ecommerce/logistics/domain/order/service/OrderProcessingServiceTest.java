package site.hexaarch.ecommerce.logistics.domain.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单处理服务单元测试，覆盖创建校验、状态流转与取消规则。
 *
 * @author kenyon
 */
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderProcessingService = new OrderProcessingService(orderRepository);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- 创建 ----------

    @Test
    void createOrderShouldGenerateOrderNumberAndPersist() {
        Order saved = orderProcessingService.createOrder("tenant-1", "customer-1",
                List.of(item("SKU-1", 2, "49.90")), address(), payment("99.80"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getOrderNumber());
        assertTrue(saved.getOrderNumber().startsWith("ORD"), "缺省订单号应由聚合按 ORD+日期+随机串生成");
        assertEquals(OrderStatus.PENDING, saved.getOrderStatus());
        assertEquals(new BigDecimal("99.80"), saved.calculateTotal());
        assertEquals(Order.MANUAL_PLATFORM, saved.getPlatform());
        assertFalse(saved.getDomainEvents().isEmpty(), "创建订单应注册 OrderCreatedEvent");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertSame(saved, captor.getValue());
    }

    @Test
    void createOrderShouldKeepProvidedOrderNumber() {
        Order saved = orderProcessingService.createOrder("tenant-1", "P-20261001-001", "customer-1",
                "张三", "zhang@example.com", List.of(item("SKU-1", 1, "10.00")), address(), payment("10.00"));

        assertEquals("P-20261001-001", saved.getOrderNumber());
        assertEquals("张三", saved.getCustomerName());
        verify(orderRepository).save(saved);
    }

    @Test
    void createOrderShouldRejectInvalidInputWithoutSaving() {
        List<OrderItem> items = List.of(item("SKU-1", 1, "10.00"));
        ShippingAddress address = address();
        PaymentInfo payment = payment("10.00");

        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", null, items, address, payment));
        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "", items, address, payment));
        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "customer-1", List.of(), address, payment));
        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "customer-1", null, address, payment));
        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "customer-1", items, null, payment));
        assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "customer-1", items, address, null));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderShouldRejectAmountMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderProcessingService.createOrder("tenant-1", "customer-1",
                        List.of(item("SKU-1", 1, "10.00")), address(), payment("12.00")));
        assertTrue(ex.getMessage().contains("amount"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ---------- 流转 ----------

    @Test
    void processOrderShouldMovePendingToProcessing() {
        Order order = pendingOrder();
        stubFind(order);

        Order result = orderProcessingService.processOrder(order.getId());

        assertEquals(OrderStatus.PROCESSING, result.getOrderStatus());
        verify(orderRepository).save(result);
    }

    @Test
    void confirmOrderShouldMovePendingToConfirmed() {
        Order order = pendingOrder();
        stubFind(order);

        assertEquals(OrderStatus.CON_FIRMED, orderProcessingService.confirmOrder(order.getId()).getOrderStatus());
    }

    @Test
    void missingOrderShouldRaiseEntityNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> orderProcessingService.processOrder("missing"));
        assertThrows(EntityNotFoundException.class, () -> orderProcessingService.cancelOrder("missing"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shippedTransitionShouldRejectPendingButAcceptProcessingAndConfirmed() {
        // 发货入口统一后的前置规则（ISS-072）：待处理订单不能直接发货，规则写在聚合状态机上，
        // 由订单状态端点、批量发货、物流单手工揽收三条入口共同遵守。
        Order pending = pendingOrder();
        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> pending.updateStatus(OrderStatus.SHIPPED));
        assertTrue(ex.getMessage().contains("不允许"));

        Order processing = orderWithStatus(OrderStatus.PROCESSING);
        processing.updateStatus(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, processing.getOrderStatus());

        Order confirmed = orderWithStatus(OrderStatus.CON_FIRMED);
        confirmed.updateStatus(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, confirmed.getOrderStatus());
    }

    @Test
    void completeOrderShouldOnlyAcceptShipped() {
        Order shipped = orderWithStatus(OrderStatus.SHIPPED);
        stubFind(shipped);
        assertEquals(OrderStatus.COMPLETED, orderProcessingService.completeOrder(shipped.getId()).getOrderStatus());

        Order processing = orderWithStatus(OrderStatus.PROCESSING);
        stubFind(processing);
        assertThrows(InvalidOperationException.class, () -> orderProcessingService.completeOrder(processing.getId()));
    }

    // ---------- 取消 ----------

    @Test
    void cancelOrderShouldAcceptPendingAndRejectShippedOrCompleted() {
        Order pending = pendingOrder();
        stubFind(pending);
        assertEquals(OrderStatus.CANCELLED, orderProcessingService.cancelOrder(pending.getId()).getOrderStatus());

        Order shipped = orderWithStatus(OrderStatus.SHIPPED);
        stubFind(shipped);
        InvalidOperationException shippedEx = assertThrows(InvalidOperationException.class,
                () -> orderProcessingService.cancelOrder(shipped.getId()));
        assertTrue(shippedEx.getMessage().contains("已发货"));

        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        stubFind(completed);
        assertThrows(InvalidOperationException.class, () -> orderProcessingService.cancelOrder(completed.getId()));
    }

    // ---------- 状态机不变式 ----------

    @Test
    void shippedOrderMustNotBeCancellable() {
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED),
                "已发货订单可取消会与 cancelOrder 的「已发货不允许取消」自相矛盾");
        assertEquals(List.of(OrderStatus.COMPLETED), OrderStatus.SHIPPED.allowedNextStatuses());
    }

    @Test
    void terminalStatusesMustNotTransition() {
        assertTrue(OrderStatus.COMPLETED.allowedNextStatuses().isEmpty());
        assertTrue(OrderStatus.CANCELLED.allowedNextStatuses().isEmpty());
        for (OrderStatus target : OrderStatus.values()) {
            assertEquals(target == OrderStatus.COMPLETED, OrderStatus.COMPLETED.canTransitionTo(target),
                    "已完成只允许幂等自流转");
            assertEquals(target == OrderStatus.CANCELLED, OrderStatus.CANCELLED.canTransitionTo(target),
                    "已取消只允许幂等自流转");
        }
    }

    @Test
    void illegalTransitionShouldThrowAndRegisterEventOnSuccess() {
        Order order = orderWithStatus(OrderStatus.SHIPPED);
        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> order.updateStatus(OrderStatus.CANCELLED));
        assertTrue(ex.getMessage().contains("不允许"));
        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());

        Order pending = pendingOrder();
        pending.updateStatus(OrderStatus.PROCESSING);
        assertEquals(OrderStatus.PROCESSING, pending.getOrderStatus());
        assertTrue(pending.getDomainEvents().stream().anyMatch(e ->
                e instanceof site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent),
                "状态流转必须产出 OrderStatusChangedEvent 供下游订阅");
    }

    @Test
    void nullStatusShouldThrowValidation() {
        assertThrows(ValidationException.class, () -> pendingOrder().updateStatus(null));
    }

    // ---------- 辅助 ----------

    private void stubFind(Order order) {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    }

    private Order pendingOrder() {
        return orderWithStatus(OrderStatus.PENDING);
    }

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .tenantId("tenant-1")
                .customerId("customer-1")
                .orderNumber("ORD-TEST-" + status.name())
                .orderItems(List.of(item("SKU-1", 1, "10.00")))
                .shippingAddress(address())
                .paymentInfo(payment("10.00"))
                .orderStatus(status)
                .build();
    }

    private OrderItem item(String sku, int quantity, String unitPrice) {
        return OrderItem.create("product-1", sku, "测试商品", quantity, new BigDecimal(unitPrice));
    }

    private ShippingAddress address() {
        return ShippingAddress.builder()
                .country("中国").province("浙江省").city("杭州市")
                .street("余杭区文一西路969号").zipCode("311121")
                .recipient("张三").phone("13800138000")
                .build();
    }

    private PaymentInfo payment(String amount) {
        return PaymentInfo.builder()
                .paymentMethod("ALIPAY").paymentStatus("SUCCESS")
                .transactionId("TRX-TEST-0001").amount(new BigDecimal(amount))
                .build();
    }
}
