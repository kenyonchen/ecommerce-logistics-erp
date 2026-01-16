package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.OrderMapper;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.CreateOrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {

    @Mock
    private OrderApplicationService orderApplicationService;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void testCreateOrder() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setProductName("Test Product");
        orderItemDto.setSkuCode("TP-001");
        orderItemDto.setQuantity(2);
        orderItemDto.setUnitPrice(10.0);
        orderItemDto.setTotalPrice(20.0);
        
        ShippingAddress shippingAddress = ShippingAddress.builder()
                .country("USA")
                .province("CA")
                .city("San Francisco")
                .street("123 Main St")
                .zipCode("94105")
                .recipient("John Doe")
                .phone("1234567890")
                .build();
        
        PaymentInfo paymentInfo = PaymentInfo.builder()
                .paymentMethod("CREDIT_CARD")
                .paymentStatus("SUCCESS")
                .transactionId("TXN001")
                .amount(BigDecimal.valueOf(20.0))
                .build();

        CreateOrderDto createOrderDto = new CreateOrderDto();
        createOrderDto.setOrderNumber("ORD-001");
        createOrderDto.setCustomerId(customerId);
        createOrderDto.setCustomerName("John Doe");
        createOrderDto.setCustomerEmail("john@example.com");
        createOrderDto.setShippingAddress(shippingAddress);
        createOrderDto.setPaymentInfo(paymentInfo);
        createOrderDto.setOrderItems(List.of(orderItemDto));

        Order mockOrder = Order.builder()
                .id(orderId)
                .orderNumber("ORD-001")
                .customerId(customerId)
                .orderStatus(OrderStatus.PENDING)
                .build();

        when(orderApplicationService.createOrder(any(String.class), any(String.class), any(List.class), any(ShippingAddress.class), any(PaymentInfo.class)))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"ORD-001\",\"customerId\":\"" + customerId + "\",\"customerName\":\"John Doe\",\"customerEmail\":\"john@example.com\",\"shippingAddress\":{\"country\":\"USA\",\"province\":\"CA\",\"city\":\"San Francisco\",\"street\":\"123 Main St\",\"zipCode\":\"94105\",\"recipient\":\"John Doe\",\"phone\":\"1234567890\"},\"paymentInfo\":{\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"SUCCESS\",\"transactionId\":\"TXN001\",\"amount\":20.0},\"orderItems\":[{\"productName\":\"Test Product\",\"skuCode\":\"TP-001\",\"quantity\":2,\"unitPrice\":10.0,\"totalPrice\":20.0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId));
    }

    @Test
    void testGetOrderById() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        
        Order mockOrder = Order.builder()
                .id(orderId)
                .orderNumber("ORD-001")
                .customerId(customerId)
                .orderStatus(OrderStatus.PENDING)
                .build();

        when(orderApplicationService.findOrderById(eq(orderId))).thenReturn(mockOrder);

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId));
    }

    @Test
    void testUpdateOrderStatus() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        
        Order mockOrder = Order.builder()
                .id(orderId)
                .orderNumber("ORD-001")
                .customerId(customerId)
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        when(orderApplicationService.updateOrderStatus(eq(orderId), eq(OrderStatus.PROCESSING)))
                .thenReturn(mockOrder);

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                .param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void testGetOrdersByCustomer() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        
        Order mockOrder = Order.builder()
                .id(orderId)
                .orderNumber("ORD-001")
                .customerId(customerId)
                .orderStatus(OrderStatus.PENDING)
                .build();

        when(orderApplicationService.findOrdersByCustomerId(eq(customerId)))
                .thenReturn(List.of(mockOrder));

        mockMvc.perform(get("/api/orders/customer/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}