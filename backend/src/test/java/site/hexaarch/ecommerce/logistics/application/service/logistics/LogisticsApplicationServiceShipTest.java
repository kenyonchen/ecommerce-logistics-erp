package site.hexaarch.ecommerce.logistics.application.service.logistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ISS-072：三条发货入口的「订单回写 SHIPPED + BR-04 出库」必须是同一份语义。
 * <p>
 * 出库唯一的落点在 {@link OrderApplicationService#updateOrderStatus} 里"订单转 SHIPPED"这一步，
 * 所以物流侧的批量发货与手工标记发货都必须经同一入口回写订单，不得各自实现一套状态推进。
 * </p>
 *
 * @author kenyon
 */
class LogisticsApplicationServiceShipTest {

    @Mock
    private LogisticsOrderRepository logisticsOrderRepository;

    @Mock
    private LogisticsChannelRepository logisticsChannelRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LogisticsTrackingService logisticsTrackingService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private LogisticsOrderResolver logisticsOrderResolver;

    @Mock
    private OrderApplicationService orderApplicationService;

    @Mock
    private FinanceApplicationService financeApplicationService;

    @InjectMocks
    private LogisticsApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(logisticsOrderRepository.save(any(LogisticsOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private LogisticsOrder pendingLogistics(String logisticsOrderId, String orderId) {
        return LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .tenantId("default-tenant")
                .orderId(orderId)
                .logisticsChannelId("CH1")
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();
    }

    private Order orderInProcessing(String orderId) {
        return Order.builder()
                .id(orderId)
                .tenantId("default-tenant")
                .customerId("C1")
                .orderNumber("SO-" + orderId)
                .orderStatus(OrderStatus.PROCESSING)
                .build();
    }

    private LogisticsChannel channel() {
        return LogisticsChannel.builder().channelId("CH1").channelCode("DEFAULT_CHANNEL").channelName("默认渠道").build();
    }

    private BatchShipRequest batchRequest(List<String> orderIds, Boolean updateOrderStatus) {
        BatchShipRequest request = new BatchShipRequest();
        request.setOrderIds(orderIds);
        request.setLogisticsChannelId("CH1");
        request.setUpdateOrderStatus(updateOrderStatus);
        return request;
    }

    @Test
    void manualShipShouldWriteOrderBackToShippedThroughTheUnifiedPath() {
        LogisticsOrder logisticsOrder = pendingLogistics("L1", "O1");
        when(logisticsOrderRepository.findById("L1")).thenReturn(Optional.of(logisticsOrder));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(orderInProcessing("O1"));

        LogisticsOrderDto dto = service.ship("L1", null, "TRK-1");

        verify(orderApplicationService).updateOrderStatus("O1", OrderStatus.SHIPPED);
        assertEquals(LogisticsStatus.COLLECTED, logisticsOrder.getLogisticsStatus());
        assertEquals("TRK-1", dto.getTrackingNumber());
    }

    @Test
    void manualShipShouldNotPersistLogisticsWhenOutboundFails() {
        when(logisticsOrderRepository.findById("L1")).thenReturn(Optional.of(pendingLogistics("L1", "O1")));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenThrow(
                new InvalidOperationException("库存不足: SKU-1, current: 0, requested: 5"));

        assertThrows(InvalidOperationException.class, () -> service.ship("L1", null, "TRK-1"));

        // 出库失败必须整体阻断发货：物流单不能留下"已揽收但订单没发、库存没扣"的假状态
        verify(logisticsOrderRepository, never()).save(any(LogisticsOrder.class));
    }

    @Test
    void batchShipShouldWriteOrderBackToShippedThroughTheUnifiedPath() {
        Order order = orderInProcessing("O1");
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of());
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenReturn(order);

        BatchShipResultDto result = service.batchShip(batchRequest(List.of("O1"), null));

        assertEquals(1, result.getSuccessCount());
        verify(orderApplicationService).updateOrderStatus("O1", OrderStatus.SHIPPED);
    }

    @Test
    void batchShipWithoutOrderSyncShouldNotTouchOrderOrInventory() {
        Order order = orderInProcessing("O1");
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of());

        BatchShipResultDto result = service.batchShip(batchRequest(List.of("O1"), false));

        assertEquals(1, result.getSuccessCount());
        verify(orderApplicationService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void batchShipRowFailureShouldNotAbortTheOtherRows() {
        Order first = orderInProcessing("O1");
        Order second = orderInProcessing("O2");
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(first);
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O2"), isNull(), isNull(), isNull()))
                .thenReturn(second);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId(any())).thenReturn(List.of());
        when(orderApplicationService.updateOrderStatus(eq("O1"), eq(OrderStatus.SHIPPED))).thenThrow(
                new InvalidOperationException("库存不足: SKU-1, current: 0, requested: 5"));
        when(orderApplicationService.updateOrderStatus(eq("O2"), eq(OrderStatus.SHIPPED))).thenReturn(second);

        BatchShipResultDto result = service.batchShip(batchRequest(List.of("O1", "O2"), null));

        // R-36 的响应契约：单行失败只记进 failures，其余行照常发货
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        ArgumentCaptor<LogisticsOrder> savedLogistics = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(savedLogistics.capture());
        assertEquals("O2", savedLogistics.getValue().getOrderId());
    }

    @Test
    void batchShipShouldFailTheRowInsteadOfSilentlyShippingACompletedOrder() {
        Order completed = Order.builder()
                .id("O1")
                .tenantId("default-tenant")
                .customerId("C1")
                .orderNumber("SO-O1")
                .orderStatus(OrderStatus.COMPLETED)
                .build();
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(completed);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of());
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenThrow(
                new InvalidOperationException("订单状态不允许从 已完成 变更为 已发货"));

        BatchShipResultDto result = service.batchShip(batchRequest(List.of("O1"), null));

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        verify(logisticsOrderRepository, never()).save(any(LogisticsOrder.class));
    }
}
