package site.hexaarch.ecommerce.logistics.application.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.service.OrderProcessingService;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单发货联动库存扣减（BR-04）的应用服务测试。
 *
 * @author kenyon
 */
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProcessingService orderProcessingService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private WarehouseApplicationService warehouseApplicationService;

    @Mock
    private LogisticsOrderRepository logisticsOrderRepository;

    @Mock
    private FinanceApplicationService financeApplicationService;

    @InjectMocks
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Order orderInProcessing(String sku, int quantity) {
        OrderItem item = OrderItem.create("P1", sku, "商品A", quantity, new BigDecimal("10.00"));
        return Order.builder()
                .id("O1")
                .tenantId("default-tenant")
                .customerId("C1")
                .orderNumber("SO-001")
                .orderItems(List.of(item))
                .orderStatus(OrderStatus.PROCESSING)
                .build();
    }


    @Test
    void shipOrderShouldDeductInventoryPerOrderItem() {
        Order order = orderInProcessing("SKU-1", 5);
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(warehouseApplicationService.resolveOutboundWarehouseId("default-tenant", "SKU-1", 5))
                .thenReturn("WH1");

        service.updateOrderStatus("O1", OrderStatus.SHIPPED);

        verify(warehouseApplicationService).inventoryOut("SKU-1", "WH1", 5);
        verify(domainEventPublisher).publishEventsFrom(order);
    }

    @Test
    void nonShipTransitionShouldNotTouchInventory() {
        Order order = orderInProcessing("SKU-1", 5);
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus("O1", OrderStatus.CON_FIRMED);

        verify(warehouseApplicationService, never()).inventoryOut(anyString(), anyString(), anyInt());
    }

    @Test
    void idempotentShipTransitionShouldNotDeductTwice() {
        Order order = Order.builder()
                .id("O1")
                .tenantId("default-tenant")
                .customerId("C1")
                .orderItems(List.of(OrderItem.create("P1", "SKU-1", "商品A", 5, new BigDecimal("10.00"))))
                .orderStatus(OrderStatus.SHIPPED)
                .build();
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus("O1", OrderStatus.SHIPPED);

        verify(warehouseApplicationService, never()).inventoryOut(anyString(), anyString(), anyInt());
    }

    @Test
    void insufficientStockShouldAbortShipBeforePersist() {
        Order order = orderInProcessing("SKU-1", 5);
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(warehouseApplicationService.resolveOutboundWarehouseId(anyString(), anyString(), anyInt()))
                .thenReturn("WH1");
        when(warehouseApplicationService.inventoryOut(eq("SKU-1"), eq("WH1"), eq(5)))
                .thenThrow(new InvalidOperationException("库存不足: SKU-1, current: 0, requested: 5"));

        assertThrows(InvalidOperationException.class, () -> service.updateOrderStatus("O1", OrderStatus.SHIPPED));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shipWithoutWarehouseShouldFailWithBusinessException() {
        Order order = orderInProcessing("SKU-1", 5);
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(warehouseApplicationService.resolveOutboundWarehouseId(anyString(), anyString(), anyInt()))
                .thenThrow(new ValidationException("当前租户下没有可用仓库，请先创建仓库"));

        assertThrows(ValidationException.class, () -> service.updateOrderStatus("O1", OrderStatus.SHIPPED));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deleteOrderShouldAlsoRemoveLogisticsOrdersDerivedFromIt() {
        Order order = orderInProcessing("SKU-1", 5);
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of(
                LogisticsOrder.builder().logisticsOrderId("L1").logisticsOrderNumber("LG-L1").orderId("O1").build(),
                LogisticsOrder.builder().logisticsOrderId("L2").logisticsOrderNumber("LG-L2").orderId("O1").build()));

        service.deleteOrder("O1");

        // 只删订单会留下指向不存在订单的孤儿物流单，并挡住「删除租户」的业务数据守卫
        verify(logisticsOrderRepository).delete("L1");
        verify(logisticsOrderRepository).delete("L2");
        verify(orderRepository).delete("O1");
        // ISS-139 / R-59：级联删物流单前必须撤掉它派生的运费应付，否则台账留一条指向空单据的活账
        verify(financeApplicationService).reverseTransactionsForDocument("L1",
                FinanceApplicationService.LOGISTICS_ORDER_REFERENCE_TYPE, "物流单 LG-L1");
        verify(financeApplicationService).reverseTransactionsForDocument("L2",
                FinanceApplicationService.LOGISTICS_ORDER_REFERENCE_TYPE, "物流单 LG-L2");
    }

    @Test
    void deleteMissingOrderShouldNotTouchLogisticsOrders() {
        when(orderRepository.findById("NOPE")).thenReturn(Optional.empty());

        service.deleteOrder("NOPE");

        verify(logisticsOrderRepository, never()).findByOrderId(anyString());
        verify(logisticsOrderRepository, never()).delete(anyString());
    }
}
