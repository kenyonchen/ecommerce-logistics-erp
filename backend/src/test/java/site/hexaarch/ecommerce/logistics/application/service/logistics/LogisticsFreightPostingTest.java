package site.hexaarch.ecommerce.logistics.application.service.logistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BR-10：发货要为物流单生成一条运费应付流水，且"发货有几条门"与"入账有几个调用点"必须一一对应。
 * <p>
 * 修复前 {@code LogisticsCompletedEventConsumer} 只打一行"记账规则未定义"的日志，
 * {@code financial_transactions} 里没有任何物流来源的行 —— 运费落在 {@code logistics_fees}，
 * 成本归集与毛利却看不到它，这是 spec/logistics.md BR-10 一直标着 ⚪ 的那格。
 * </p>
 * <p>
 * 按 R-58 定标的四条口径在这里逐条钉住：入账时点是"货已交出"（建单只预估不入账）、
 * 金额只读已落库的 {@code totalFee}（不重算）、同单同类流水只记一条（幂等）、
 * 历史无费用行的单据不入账（不凭空编金额）。
 * </p>
 *
 * @author kenyon
 */
class LogisticsFreightPostingTest {

    private static final String TENANT_ID = "default-tenant";
    private static final BigDecimal BASE_FEE = new BigDecimal("8.00");
    private static final BigDecimal WEIGHT_FEE = new BigDecimal("5.00");
    private static final BigDecimal TOTAL_FEE = new BigDecimal("13.00");

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

    private LogisticsFee fee(String base, String weight, String volume, String total) {
        return LogisticsFee.builder()
                .feeId("FEE-1")
                .baseFee(new BigDecimal(base))
                .weightFee(new BigDecimal(weight))
                .volumeFee(new BigDecimal(volume))
                .totalFee(new BigDecimal(total))
                .currency("CNY")
                .build();
    }

    private LogisticsOrder logistics(String logisticsOrderId, String orderId, LogisticsFee logisticsFee) {
        return LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .logisticsOrderNumber("LG-" + logisticsOrderId)
                .tenantId(TENANT_ID)
                .orderId(orderId)
                .logisticsChannelId("CH1")
                .carrier("默认渠道")
                .logisticsStatus(LogisticsStatus.PENDING)
                .logisticsFee(logisticsFee)
                .build();
    }

    private Order order(String orderId, OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .tenantId(TENANT_ID)
                .customerId("C1")
                .orderNumber("SO-" + orderId)
                .orderStatus(status)
                .build();
    }

    private LogisticsChannel channel() {
        return LogisticsChannel.builder()
                .channelId("CH1")
                .channelCode("DEFAULT_CHANNEL")
                .channelName("默认渠道")
                .basePrice(BASE_FEE)
                .pricePerGram(new BigDecimal("0.0050"))
                .currency("CNY")
                .build();
    }

    /**
     * 建单即发货（第四条发货门）走的是同一条订单状态机，所以订单要处在可发状态。
     */
    private void stubCreatePath(Order order) {
        when(logisticsOrderResolver.resolveOrder(eq(TENANT_ID), eq(order.getId()), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(orderApplicationService.updateOrderStatus(order.getId(), OrderStatus.SHIPPED)).thenReturn(order);
    }

    private CreateLogisticsRequest createRequest(String orderId, Boolean shipImmediately) {
        CreateLogisticsRequest request = new CreateLogisticsRequest();
        request.setOrderId(orderId);
        request.setLogisticsChannelId("CH1");
        request.setShipImmediately(shipImmediately);
        return request;
    }

    private ArgumentCaptor<BigDecimal> postAmountCaptor() {
        return ArgumentCaptor.forClass(BigDecimal.class);
    }

    @Test
    void manualShipPostsFreightExpenseWithLedgerFields() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(order("O1", OrderStatus.PROCESSING));

        service.ship("L1", null, "TRK-1");

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE),
                eq(TOTAL_FEE), eq("CNY"), eq("L1"), eq(LogisticsApplicationService.FREIGHT_REFERENCE_TYPE),
                description.capture(), eq(TENANT_ID));
        // 备注要能人读回溯到那张物流单与费用构成（R-58：可追溯）
        assertTrue(description.getValue().contains("LG-L1"), "入账备注必须带物流单号：" + description.getValue());
        assertTrue(description.getValue().contains("8.00"), "入账备注必须带首重：" + description.getValue());
        assertTrue(description.getValue().contains("5.00"), "入账备注必须带续重：" + description.getValue());
    }

    @Test
    void carrierlessOrderPostsFreightWithoutNullLiteralInDescription() {
        // 事件自动建的物流单没有 carrier（只有渠道 ID），R-18 台账实测抓到备注渲染成"承运商 null"
        LogisticsOrder carrierless = LogisticsOrder.builder()
                .logisticsOrderId("L1")
                .logisticsOrderNumber("LG-L1")
                .tenantId(TENANT_ID)
                .orderId("O1")
                .logisticsChannelId("CH1")
                .logisticsStatus(LogisticsStatus.PENDING)
                .logisticsFee(fee("8.00", "5.00", "0.00", "13.00"))
                .build();
        when(logisticsOrderRepository.findById("L1")).thenReturn(Optional.of(carrierless));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(order("O1", OrderStatus.PROCESSING));

        service.ship("L1", null, "TRK-1");

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE),
                eq(TOTAL_FEE), eq("CNY"), eq("L1"), eq(LogisticsApplicationService.FREIGHT_REFERENCE_TYPE),
                description.capture(), eq(TENANT_ID));
        assertFalse(description.getValue().contains("null"),
                "承运商未知时备注不得把 null 字面量写进台账：" + description.getValue());
        assertTrue(description.getValue().contains("首重 8.00 + 续重 5.00"),
                "承运商未知时费用构成仍必须完整：" + description.getValue());
    }

    @Test
    void batchShipPostsFreightForTheOrderItShips() {
        Order order = order("O1", OrderStatus.PROCESSING);
        when(logisticsOrderResolver.resolveOrder(eq(TENANT_ID), eq("O1"), isNull(), isNull(), isNull()))
                .thenReturn(order);
        when(logisticsOrderResolver.resolveChannel("CH1", null)).thenReturn(channel());
        when(logisticsOrderRepository.findByOrderId("O1")).thenReturn(List.of());
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED)).thenReturn(order);
        when(productRepository.findBySkuCode(any())).thenReturn(Optional.empty());

        BatchShipRequest request = new BatchShipRequest();
        request.setOrderIds(List.of("O1"));
        request.setLogisticsChannelId("CH1");
        service.batchShip(request);

        // SKU 无重量 ⇒ 只收首重 8.00，入账金额就是这条预估，不多不少；
        // 关联单据取批量发货当场新建的那张物流单 ID（由聚合生成，不能写死）
        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE),
                eq(BASE_FEE), eq("CNY"), eq(saved.getValue().getLogisticsOrderId()),
                eq(LogisticsApplicationService.FREIGHT_REFERENCE_TYPE), any(), eq(TENANT_ID));
    }

    @Test
    void createWithShipImmediatelyPostsFreight() {
        Order order = order("O1", OrderStatus.PROCESSING);
        stubCreatePath(order);
        when(productRepository.findBySkuCode(any())).thenReturn(Optional.empty());

        service.createLogisticsOrder(createRequest("O1", true));

        ArgumentCaptor<LogisticsOrder> saved = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(logisticsOrderRepository).save(saved.capture());
        ArgumentCaptor<BigDecimal> amount = postAmountCaptor();
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE),
                amount.capture(), eq("CNY"), eq(saved.getValue().getLogisticsOrderId()),
                eq(LogisticsApplicationService.FREIGHT_REFERENCE_TYPE), description.capture(), eq(TENANT_ID));
        assertEquals(0, BASE_FEE.compareTo(amount.getValue()), "建单即发货入账的必须是预估运费本体");
        assertTrue(description.getValue().contains(saved.getValue().getLogisticsOrderNumber()),
                "入账备注必须带新建物流单的单号");
    }

    @Test
    void plainCreateEstimatesFreightButDoesNotPost() {
        Order order = order("O1", OrderStatus.PROCESSING);
        stubCreatePath(order);
        when(productRepository.findBySkuCode(any())).thenReturn(Optional.empty());

        service.createLogisticsOrder(createRequest("O1", false));

        // 货没交出去就不该有应付——建单只预估（R-58 时点口径）
        verifyNoInteractions(financeApplicationService);
    }

    @Test
    void orchestrationCreatePathNeverPosts() {
        when(logisticsOrderResolver.resolveChannelByIdOrCode("DEFAULT_CHANNEL")).thenReturn(channel());
        when(orderRepository.findById("O1")).thenReturn(Optional.of(order("O1", OrderStatus.PENDING)));

        service.createLogisticsOrder(TENANT_ID, "O1", "DEFAULT_CHANNEL");

        // 事件/编排链只补建物流单，推进订单状态另有出口；这条必须保持"不入账"，
        // 否则自动建单会在货没走的时候凭空造出应付
        verifyNoInteractions(financeApplicationService);
    }

    @Test
    void alreadyPostedFreightIsNotPostedTwice() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(order("O1", OrderStatus.SHIPPED));
        when(financeApplicationService.existsFinancialTransaction("L1",
                LogisticsApplicationService.FREIGHT_REFERENCE_TYPE, TransactionType.EXPENSE)).thenReturn(true);

        service.ship("L1", null, "TRK-1");

        verify(financeApplicationService, never()).createFinancialTransaction(any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void statusPushIntoShippedStatesPostsFreight() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));

        service.updateLogisticsStatus("L1", LogisticsStatus.IN_TRANSIT, "运输中");

        // 状态端点也能把单据落到"货已交出"，漏了它运费就永远不进台账（R-58）
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE), eq(TOTAL_FEE),
                eq("CNY"), eq("L1"), eq(LogisticsApplicationService.FREIGHT_REFERENCE_TYPE), any(), eq(TENANT_ID));
    }

    @Test
    void statusPushBeforeCollectionDoesNotPost() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));

        service.updateLogisticsStatus("L1", LogisticsStatus.LABEL_GENERATED, "已打单");

        verifyNoInteractions(financeApplicationService);
    }

    @Test
    void feelessHistoricalOrderShipsWithoutPosting() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", null)));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(order("O1", OrderStatus.PROCESSING));

        service.ship("L1", null, "TRK-1");

        // ISS-137 之前建的历史物流单没有费用行，宁可台账少一行也不能凭猜测编金额
        verifyNoInteractions(financeApplicationService);
    }

    @Test
    void deletingLogisticsOrderReversesItsFreightExpenseFirst() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));

        service.delete("L1");

        // ISS-139：修复前这里只删单据行，台账里那条 expense/pending 原样留着指向空单据
        verify(financeApplicationService).reverseTransactionsForDocument("L1",
                LogisticsApplicationService.FREIGHT_REFERENCE_TYPE, "物流单 LG-L1");
        verify(logisticsOrderRepository).delete("L1");
    }

    @Test
    void deleteIsBlockedWhenFreightExpenseAlreadyConfirmed() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("8.00", "5.00", "0.00", "13.00"))));
        org.mockito.Mockito.doThrow(new BusinessException("已确认成功，不能连同单据一起删除"))
                .when(financeApplicationService).reverseTransactionsForDocument("L1",
                        LogisticsApplicationService.FREIGHT_REFERENCE_TYPE, "物流单 LG-L1");

        assertThrows(BusinessException.class, () -> service.delete("L1"));

        // 已过账的分录不能被"顺手删单"抹掉：撤销失败时单据必须留在库里
        verify(logisticsOrderRepository, never()).delete(any());
    }

    @Test
    void zeroFreightDoesNotPost() {
        when(logisticsOrderRepository.findById("L1"))
                .thenReturn(Optional.of(logistics("L1", "O1", fee("0.00", "0.00", "0.00", "0.00"))));
        when(orderApplicationService.updateOrderStatus("O1", OrderStatus.SHIPPED))
                .thenReturn(order("O1", OrderStatus.PROCESSING));

        service.ship("L1", null, "TRK-1");

        // FinancialTransaction.create() 拒绝零金额，这里先跳过，不能让发货整单回滚
        verifyNoInteractions(financeApplicationService);
    }
}
