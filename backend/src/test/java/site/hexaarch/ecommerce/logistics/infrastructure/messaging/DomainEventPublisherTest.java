package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import site.hexaarch.ecommerce.logistics.domain.customer.event.CustomerCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.event.FinancialTransactionCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.platform.event.PlatformIntegrationCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.purchase.event.PurchaseOrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 领域事件双通道发布测试：进程内通道始终发布，RocketMQ 通道按事件所属上下文包名路由到 topic。
 */
class DomainEventPublisherTest {

    private ApplicationEventPublisher applicationEventPublisher;
    private RocketMQTemplate rocketMQTemplate;
    private ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private DomainEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        rocketMQTemplateProvider = mock(ObjectProvider.class);
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(rocketMQTemplate);
        publisher = new DomainEventPublisher(applicationEventPublisher, rocketMQTemplateProvider);
    }

    /**
     * 反射回退路径使用的聚合替身：只要有一个名为 domainEvents 的字段即可被识别。
     */
    static class EventCarrier {
        final List<Object> domainEvents = new ArrayList<>();
    }

    private EventCarrier carrierOf(Object... events) {
        EventCarrier carrier = new EventCarrier();
        carrier.domainEvents.addAll(List.of(events));
        return carrier;
    }

    @Test
    void shouldRouteEachContextPackageToItsOwnTopic() {
        publisher.publishEventsFrom(carrierOf(
                orderEvent(), customerEvent(), logisticsEvent(), inventoryEvent(),
                financeEvent(), purchaseEvent(), productEvent(), platformEvent()));

        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(rocketMQTemplate, times(8)).convertAndSend(topics.capture(), payloads.capture());

        List<String> sent = topics.getAllValues();
        assertTrue(sent.contains("order-events"), "订单事件应发往 order-events，实际: " + sent);
        assertTrue(sent.contains("customer-events"), "客户事件应发往 customer-events，实际: " + sent);
        assertTrue(sent.contains("logistics-events"), "物流事件应发往 logistics-events，实际: " + sent);
        assertTrue(sent.contains("inventory-events"), "仓储事件应发往 inventory-events，实际: " + sent);
        assertTrue(sent.contains("finance-events"), "财务事件应发往 finance-events，实际: " + sent);
        assertTrue(sent.contains("purchase-events"), "采购事件应发往 purchase-events，实际: " + sent);
        assertTrue(sent.contains("product-events"), "商品事件应发往 product-events（ISS-039 回归防护），实际: " + sent);
        assertTrue(sent.contains("platform-events"), "平台事件应发往 platform-events（ISS-039 回归防护），实际: " + sent);
    }

    @Test
    void productAndPlatformEventsShouldAlsoReachInProcessChannel() {
        ProductCreatedEvent product = productEvent();
        PlatformIntegrationCreatedEvent platform = platformEvent();

        publisher.publishEventsFrom(carrierOf(product, platform));

        verify(applicationEventPublisher).publishEvent(product);
        verify(applicationEventPublisher).publishEvent(platform);
    }

    @Test
    void shouldSkipMqChannelWhenTemplateIsNotConfigured() {
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(null);

        OrderStatusChangedEvent event = orderEvent();
        publisher.publishEventsFrom(carrierOf(event));

        verify(applicationEventPublisher).publishEvent(event);
        verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void shouldClearPublishedEvents() {
        EventCarrier carrier = carrierOf(productEvent(), customerEvent());

        publisher.publishEventsFrom(carrier);

        assertTrue(carrier.domainEvents.isEmpty(), "发布完成后应清空待发布事件，避免重复投递");
    }

    @Test
    void shouldNotPublishUnknownPackageToMq() {
        Object foreignEvent = new Object();

        publisher.publishEventsFrom(carrierOf(foreignEvent));

        verify(applicationEventPublisher).publishEvent(foreignEvent);
        verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void mqFailureShouldNotBreakInProcessPublication() {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rocketMQTemplate).convertAndSend(anyString(), any(Object.class));

        LogisticsCompletedEvent event = logisticsEvent();
        publisher.publishEventsFrom(carrierOf(event));

        verify(applicationEventPublisher).publishEvent(event);
    }

    private OrderStatusChangedEvent orderEvent() {
        return OrderStatusChangedEvent.builder()
                .orderId("ORD-20261001-0001")
                .oldStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CON_FIRMED)
                .changedAt(LocalDateTime.now())
                .build();
    }

    private CustomerCreatedEvent customerEvent() {
        return new CustomerCreatedEvent("CUS-001", "张三", "zhangsan@example.com", LocalDateTime.now());
    }

    private LogisticsCompletedEvent logisticsEvent() {
        return LogisticsCompletedEvent.builder()
                .logisticsOrderId("LG-001")
                .orderId("ORD-20261001-0001")
                .completedAt(LocalDateTime.now())
                .build();
    }

    private InventoryShortageEvent inventoryEvent() {
        return InventoryShortageEvent.builder()
                .warehouseId("WH-001")
                .productId("SKU-001")
                .sku("SKU-001")
                .currentQuantity(2)
                .threshold(10)
                .build();
    }

    private FinancialTransactionCreatedEvent financeEvent() {
        return new FinancialTransactionCreatedEvent("TXN-001", "default-tenant",
                TransactionType.INCOME, new BigDecimal("199.00"), "CNY",
                "ORD-20261001-0001", "ORDER", LocalDateTime.now());
    }

    private PurchaseOrderCreatedEvent purchaseEvent() {
        return new PurchaseOrderCreatedEvent("PO-001", "default-tenant", "SUP-001", "WH-001", LocalDateTime.now());
    }

    private ProductCreatedEvent productEvent() {
        return ProductCreatedEvent.builder()
                .productId("PRD-001")
                .tenantId("default-tenant")
                .productName("无线鼠标")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private PlatformIntegrationCreatedEvent platformEvent() {
        return new PlatformIntegrationCreatedEvent(UUID.randomUUID(), "default-tenant", PlatformName.AMAZON);
    }
}
