package site.hexaarch.ecommerce.logistics.application.service.logistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ISS-137：运费必须在「创建物流单」这一步按渠道计费口径落库。
 * <p>
 * 修复前 {@code logistics_fees} 恒 0 行 —— 计费公式只活在 {@code GET /api/logistics-channels/{id}/fee}
 * 的前端试算弹窗里，三条创建路径（HTTP 建单、编排建单、批量发货）都不写 {@code LogisticsFee}，
 * 于是列表与详情页的「运费」列永远是 "-"，毛利与应付也无从归集。
 * 按 R-55：计费点只有创建一处；发货/状态推进/打单都不重算，已有费用（导入回传的平台实际运费）不覆盖。
 * </p>
 *
 * @author kenyon
 */
class LogisticsFreightEstimationTest {

    /**
     * 首重 8.00、续重 0.0050/克，与库内 DEFAULT_CHANNEL 实测口径一致
     */
    private static final BigDecimal BASE_PRICE = new BigDecimal("8.00");
    private static final BigDecimal PRICE_PER_GRAM = new BigDecimal("0.0050");

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

    /**
     * BR-10 的联动依赖：批量发货会顺带入账，这里只关心运费算得对不对，
     * 入账行为由 {@code LogisticsFreightPostingTest} 逐路径盯。
     */
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

    private LogisticsChannel channel() {
        return LogisticsChannel.builder()
                .channelId("CH1")
                .channelCode("DEFAULT_CHANNEL")
                .channelName("默认渠道")
                .basePrice(BASE_PRICE)
                .pricePerGram(PRICE_PER_GRAM)
                .currency("CNY")
                .build();
    }

    private OrderItem item(String skuCode, int quantity) {
        return OrderItem.create("P-" + skuCode, skuCode, "商品 " + skuCode, quantity, new BigDecimal("10.00"));
    }

    private Order orderWith(String orderId, OrderItem... items) {
        return Order.builder()
                .id(orderId)
                .tenantId("default-tenant")
                .customerId("C1")
                .orderNumber("SO-" + orderId)
                .orderStatus(OrderStatus.PROCESSING)
                .orderItems(List.of(items))
                .build();
    }

    /**
     * 订单项按 sku_code 关联 SKU，重量单位为克（实测 skus.weight 即克，与 price_per_gram 同单位）。
     */
    private void stubSkuWeight(String skuCode, Integer weightInGrams) {
        when(productRepository.findBySkuCode(skuCode)).thenReturn(Optional.of(
                Product.builder()
                        .id("P-" + skuCode)
                        .tenantId("default-tenant")
                        .name("商品 " + skuCode)
                        .skus(List.of(SKU.builder().skuCode(skuCode).weightInGrams(weightInGrams).build()))
                        .build()));
    }

    private CreateLogisticsRequest createRequest(String orderId) {
        CreateLogisticsRequest request = new CreateLogisticsRequest();
        request.setOrderId(orderId);
        request.setLogisticsChannelId("CH1");
        return request;
    }

    private LogisticsFee persistedFee(ArgumentCaptor<LogisticsOrder> captor) {
        LogisticsOrder saved = captor.getValue();
        LogisticsFee fee = saved.getLogisticsFee();
        assertNotNull(fee, "创建物流单必须按渠道计费口径落库运费（ISS-137），实际 logistics_fee 为空");
        return fee;
    }

    @Test
    void createLogisticsOrderEstimatesFreightFromSkuWeights() {
        // 500克 ×2 + 300克 ×1 = 1300克 ⇒ 续重 6.50，首重 8.00 ⇒ 合计 14.50
        Order order = orderWith("O1", item("SKU-A", 2), item("SKU-B", 1));
        stubSkuWeight("SKU-A", 500);
        stubSkuWeight("SKU-B", 300);
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());

        LogisticsOrderDto dto = service.createLogisticsOrder(createRequest("O1"));

        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        LogisticsFee fee = persistedFee(saved);
        assertEquals(0, BASE_PRICE.compareTo(fee.getBaseFee()), "首重应等于渠道 basePrice");
        assertEquals(new BigDecimal("6.50"), fee.getWeightFee());
        assertEquals(new BigDecimal("14.50"), fee.getTotalFee());
        assertEquals("CNY", fee.getCurrency());
        // 契约面也必须能看到分解（spec/logistics 验收 5：基础费 + 重量费 + 币种字段完整），
        // 否则"库里写了、接口看不见"这种半成品状态会被 code 200 掩盖
        assertEquals(new BigDecimal("8.00"), dto.getBaseFee());
        assertEquals(new BigDecimal("6.50"), dto.getWeightFee());
        assertEquals(new BigDecimal("14.50"), dto.getTotalFee());
        assertEquals("CNY", dto.getCurrency());
    }

    @Test
    void batchShipEstimatesFreightForTheLogisticsOrderItCreates() {
        Order order = orderWith("O1", item("SKU-A", 3));
        stubSkuWeight("SKU-A", 100);
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of());
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenReturn(order);

        BatchShipRequest request = new BatchShipRequest();
        request.setOrderIds(List.of("O1"));
        request.setLogisticsChannelId("CH1");
        service.batchShip(request);

        // 300克 × 0.0050 = 1.50 续重 + 8.00 首重
        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        assertEquals(new BigDecimal("9.50"), persistedFee(saved).getTotalFee());
    }

    @Test
    void orchestrationCreatePathEstimatesFreightToo() {
        Order order = orderWith("O1", item("SKU-A", 1));
        stubSkuWeight("SKU-A", 1000);
        when(logisticsOrderResolver.resolveChannelByIdOrCode("DEFAULT_CHANNEL")).thenReturn(channel());
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order));

        LogisticsOrder created = service.createLogisticsOrder("default-tenant", "O1", "DEFAULT_CHANNEL");

        // 1000克 × 0.0050 = 5.00 续重 + 8.00 首重
        assertNotNull(created);
        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        assertEquals(new BigDecimal("13.00"), persistedFee(saved).getTotalFee());
    }

    @Test
    void missingSkuWeightFallsBackToBaseFeeOnly() {
        Order order = orderWith("O1", item("SKU-X", 4));
        when(productRepository.findBySkuCode("SKU-X")).thenReturn(Optional.empty());
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());

        service.createLogisticsOrder(createRequest("O1"));

        // SKU 未维护重量（BR-06）时不能因此不建单、也不能凭空算出续重：只收首重
        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        LogisticsFee fee = persistedFee(saved);
        assertEquals(new BigDecimal("0.00"), fee.getWeightFee());
        assertEquals(BASE_PRICE, fee.getTotalFee());
    }

    @Test
    void existingFeeIsNeverOverwritten() {
        Order order = orderWith("O1", item("SKU-A", 2));
        stubSkuWeight("SKU-A", 500);
        when(logisticsOrderResolver.resolveOrder(eq("default-tenant"), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());

        // 复用已有 PENDING 物流单的那条路：批量发货命中它时不得重算
        LogisticsOrder pending = LogisticsOrder.builder()
                .logisticsOrderId("L1")
                .tenantId("default-tenant")
                .orderId("O1")
                .logisticsChannelId("CH1")
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();
        pending.setLogisticsFee(LogisticsFee.builder()
                .totalFee(new BigDecimal("23.50"))
                .currency("CNY")
                .build());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of(pending));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenReturn(order);
        BatchShipRequest batch = new BatchShipRequest();
        batch.setOrderIds(List.of("O1"));
        batch.setLogisticsChannelId("CH1");

        service.batchShip(batch);

        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        assertEquals(new BigDecimal("23.50"), saved.getValue().getLogisticsFee().getTotalFee(),
                "已有运费（导入回传的平台实际运费）必须原样保留，R-55 只在缺失时预估");
    }

    @Test
    void shipAndStatusChangeNeverInventAFreight() {
        LogisticsOrder legacy = LogisticsOrder.builder()
                .logisticsOrderId("L1")
                .tenantId("default-tenant")
                .orderId("O1")
                .logisticsChannelId("CH1")
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();
        when(logisticsOrderRepository.findById("L1")).thenReturn(Optional.of(legacy));
        when(orderApplicationService.updateOrderStatus(eq("O1"), eq(OrderStatus.SHIPPED)))
                .thenReturn(orderWith("O1", item("SKU-A", 1)));

        service.ship("L1", null, "TRK-1");

        // R-55 的负例：计费点只有创建一处，发货不回填、不重算，否则改渠道价格会拖动历史运费
        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        assertNull(saved.getValue().getLogisticsFee());
        verify(productRepository, never()).findBySkuCode(any());
    }
}
