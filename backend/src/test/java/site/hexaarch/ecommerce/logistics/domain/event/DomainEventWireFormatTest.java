package site.hexaarch.ecommerce.logistics.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 领域事件的 MQ 线上格式回归测试（ISS-052）。
 * <p>
 * 事件类只有 builder 形态的私有构造且字段全 final，Jackson 找不到 creator，
 * RocketMQ 消费端在 `cannot convert message` 上反复重试；这里锁死「发出去的字节能读回来」。
 * 事件类没有 equals，因此逐字段比对。
 * </p>
 *
 * @author kenyon
 */
class DomainEventWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 库存不足事件可反序列化() throws Exception {
        InventoryShortageEvent event = InventoryShortageEvent.builder()
                .warehouseId("wh-1")
                .tenantId("tenant-1")
                .productId("p-1")
                .sku("SKU-1")
                .currentQuantity(0)
                .threshold(10)
                .occurredAt(LocalDateTime.of(2026, 10, 9, 12, 0))
                .build();

        InventoryShortageEvent restored =
                objectMapper.readValue(objectMapper.writeValueAsString(event), InventoryShortageEvent.class);

        assertEquals("wh-1", restored.getWarehouseId());
        assertEquals("tenant-1", restored.getTenantId());
        assertEquals("p-1", restored.getProductId());
        assertEquals("SKU-1", restored.getSku());
        assertEquals(0, restored.getCurrentQuantity());
        assertEquals(10, restored.getThreshold());
        assertEquals(event.getOccurredAt(), restored.getOccurredAt());
    }

    @Test
    void 物流完成事件可反序列化() throws Exception {
        LogisticsCompletedEvent event = LogisticsCompletedEvent.builder()
                .logisticsOrderId("lg-1")
                .orderId("o-1")
                .completedAt(LocalDateTime.of(2026, 10, 9, 12, 0))
                .build();

        LogisticsCompletedEvent restored =
                objectMapper.readValue(objectMapper.writeValueAsString(event), LogisticsCompletedEvent.class);

        assertEquals("lg-1", restored.getLogisticsOrderId());
        assertEquals("o-1", restored.getOrderId());
        assertEquals(event.getCompletedAt(), restored.getCompletedAt());
    }

    @Test
    void 订单状态变更事件可反序列化() throws Exception {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId("o-1")
                .oldStatus(OrderStatus.PROCESSING)
                .newStatus(OrderStatus.SHIPPED)
                .changedAt(LocalDateTime.of(2026, 10, 9, 12, 0))
                .build();

        OrderStatusChangedEvent restored =
                objectMapper.readValue(objectMapper.writeValueAsString(event), OrderStatusChangedEvent.class);

        assertEquals("o-1", restored.getOrderId());
        assertEquals(OrderStatus.PROCESSING, restored.getOldStatus());
        assertEquals(OrderStatus.SHIPPED, restored.getNewStatus());
        assertEquals(event.getChangedAt(), restored.getChangedAt());
    }

    @Test
    void 订单创建事件含嵌套值对象可反序列化() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId("o-1")
                .tenantId("tenant-1")
                .customerId("c-1")
                .orderItems(List.of(OrderItem.builder()
                        .id("i-1")
                        .productId("p-1")
                        .sku("SKU-1")
                        .productName("商品")
                        .quantity(2)
                        .unitPrice(new BigDecimal("9.90"))
                        .totalPrice(new BigDecimal("19.80"))
                        .build()))
                .shippingAddress(ShippingAddress.builder()
                        .country("CN").province("广东").city("深圳")
                        .street("科技园1号").zipCode("518000")
                        .recipient("张三").phone("13800000000")
                        .build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentMethod("CREDIT_CARD").paymentStatus("PAID")
                        .transactionId("TXN-1").amount(new BigDecimal("19.80"))
                        .build())
                .createdAt(LocalDateTime.of(2026, 10, 9, 12, 0))
                .build();

        OrderCreatedEvent restored =
                objectMapper.readValue(objectMapper.writeValueAsString(event), OrderCreatedEvent.class);

        assertEquals("o-1", restored.getOrderId());
        assertEquals("tenant-1", restored.getTenantId());
        assertEquals("c-1", restored.getCustomerId());
        assertEquals(event.getCreatedAt(), restored.getCreatedAt());
        OrderItem item = restored.getOrderItems().get(0);
        assertEquals("SKU-1", item.getSku());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("19.80"), item.getTotalPrice());
        assertEquals("科技园1号", restored.getShippingAddress().getStreet());
        assertEquals("TXN-1", restored.getPaymentInfo().getTransactionId());
    }
}
