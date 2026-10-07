package site.hexaarch.ecommerce.logistics.application.service.purchase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;
import site.hexaarch.ecommerce.logistics.domain.purchase.service.PurchaseOrderService;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购应用服务的编排测试：到货入库/采购应付的同步联动、租户校验与事件发布。
 *
 * @author kenyon
 */
class PurchaseApplicationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WAREHOUSE = "wh-1";
    private static final String ORDER_ID = "PO-1";

    @Mock
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private WarehouseApplicationService warehouseApplicationService;

    @Mock
    private FinanceApplicationService financeApplicationService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private PurchaseApplicationService purchaseApplicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        purchaseApplicationService = new PurchaseApplicationService(purchaseOrderService,
                warehouseApplicationService, financeApplicationService, domainEventPublisher);
        // 默认让收货仓库归属当前租户，只有用例想验证租户门禁时才覆盖它
        when(warehouseApplicationService.findWarehouseById(WAREHOUSE)).thenReturn(warehouse(TENANT));
    }

    @Test
    void deliveredShouldStockInEveryLineAndBookPurchasePayable() {
        // BR-08/BR-09/BR-10：到货不是只改状态，库存与应付必须在同一次调用里落地
        PurchaseOrder purchaseOrder = order(PurchaseStatus.DELIVERED, new BigDecimal("1000.00"),
                item("SKU-001", 2, "10.00"), item("SKU-002", 5, "196.00"));
        when(purchaseOrderService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID)).thenReturn(purchaseOrder);

        purchaseApplicationService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID);

        verify(warehouseApplicationService).inventoryIn("SKU-001", WAREHOUSE, 2);
        verify(warehouseApplicationService).inventoryIn("SKU-002", WAREHOUSE, 5);
        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(financeApplicationService).createFinancialTransaction(eq(TransactionType.EXPENSE), amount.capture(),
                eq("CNY"), eq(ORDER_ID), eq(PurchaseApplicationService.PAYABLE_REFERENCE_TYPE), anyString(), eq(TENANT));
        assertEquals(0, new BigDecimal("1000.00").compareTo(amount.getValue()));
        verify(domainEventPublisher).publishEventsFrom(purchaseOrder);
    }

    @Test
    void deliveredWithoutLinesShouldTouchNeitherInventoryNorFinance() {
        PurchaseOrder purchaseOrder = order(PurchaseStatus.DELIVERED, BigDecimal.ZERO);
        when(purchaseOrderService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID)).thenReturn(purchaseOrder);

        purchaseApplicationService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID);

        verify(warehouseApplicationService, never()).inventoryIn(anyString(), anyString(), anyInt());
        verify(financeApplicationService, never()).createFinancialTransaction(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void deliveredWithZeroAmountShouldStillReceiveGoodsButNotBookAPayable() {
        // 历史单头可能没有金额：收货照做，零元应付不写流水
        PurchaseOrder purchaseOrder = order(PurchaseStatus.DELIVERED, BigDecimal.ZERO, item("SKU-001", 3, "10.00"));
        when(purchaseOrderService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID)).thenReturn(purchaseOrder);

        purchaseApplicationService.markPurchaseOrderAsDelivered(TENANT, ORDER_ID);

        verify(warehouseApplicationService).inventoryIn("SKU-001", WAREHOUSE, 3);
        verify(financeApplicationService, never()).createFinancialTransaction(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void createShouldRejectWarehouseOfAnotherTenant() {
        when(warehouseApplicationService.findWarehouseById(WAREHOUSE)).thenReturn(warehouse("other-tenant"));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> purchaseApplicationService.createPurchaseOrder(TENANT, "sup-1", "供应商A", WAREHOUSE, List.of()));
        assertEquals("收货仓库不属于当前租户: " + WAREHOUSE, exception.getMessage());
        verify(purchaseOrderService, never()).createPurchaseOrder(anyString(), anyString(), anyString(), anyString(),
                any());
    }

    @Test
    void createShouldRejectUnknownWarehouse() {
        when(warehouseApplicationService.findWarehouseById(WAREHOUSE))
                .thenThrow(new EntityNotFoundException("仓库不存在: " + WAREHOUSE));

        assertThrows(ValidationException.class,
                () -> purchaseApplicationService.createPurchaseOrder(TENANT, "sup-1", "供应商A", WAREHOUSE, List.of()));
    }

    @Test
    void createShouldStillAcceptWarehouseWithoutTenantOwnership() {
        // 线上历史仓库行没有租户归属，不能因此把老租户的采购入口堵死
        when(warehouseApplicationService.findWarehouseById(WAREHOUSE)).thenReturn(warehouse(null));
        when(purchaseOrderService.createPurchaseOrder(eq(TENANT), anyString(), anyString(), anyString(), any()))
                .thenReturn(order(PurchaseStatus.PENDING, BigDecimal.ZERO));
        when(purchaseOrderService.findPurchaseOrderById(TENANT, ORDER_ID))
                .thenReturn(order(PurchaseStatus.PENDING, BigDecimal.ZERO));

        purchaseApplicationService.createPurchaseOrder(TENANT, "sup-1", "供应商A", WAREHOUSE, List.of());

        verify(purchaseOrderService).createPurchaseOrder(eq(TENANT), eq("sup-1"), eq("供应商A"), eq(WAREHOUSE),
                eq(List.of()));
    }

    @Test
    void createShouldPersistLinesInOneCallAndPublishEventsOnce() {
        PurchaseOrder purchaseOrder = order(PurchaseStatus.PENDING, BigDecimal.ZERO, item("SKU-001", 2, "10.00"));
        PurchaseOrder reloaded = order(PurchaseStatus.PENDING, new BigDecimal("20.00"),
                item("SKU-001", 2, "10.00"));
        when(purchaseOrderService.createPurchaseOrder(eq(TENANT), anyString(), anyString(), anyString(), any()))
                .thenReturn(purchaseOrder);
        when(purchaseOrderService.findPurchaseOrderById(TENANT, ORDER_ID)).thenReturn(reloaded);

        PurchaseOrder result = purchaseApplicationService.createPurchaseOrder(TENANT, "sup-1", "供应商A", WAREHOUSE,
                List.of(PurchaseOrderItem.create("SKU-001", "商品A", 2, new BigDecimal("10.00"))));

        // 明细随单头一次提交，不能退化成"建单 + N 次加行"
        verify(purchaseOrderService, times(1)).createPurchaseOrder(anyString(), anyString(), anyString(), anyString(),
                any());
        verify(domainEventPublisher, times(1)).publishEventsFrom(purchaseOrder);
        assertSame(reloaded, result, "创建出参也要回读，否则 items[].id 为 null，与加行/改行接口的契约不一致");
    }

    @Test
    void addItemShouldReReadTheOrderSoTheResponseCarriesDatabaseItemIds() {
        PurchaseOrder saved = order(PurchaseStatus.PENDING, BigDecimal.ZERO);
        PurchaseOrder reloaded = order(PurchaseStatus.PENDING, new BigDecimal("20.00"), item("SKU-001", 2, "10.00"));
        when(purchaseOrderService.addPurchaseOrderItem(eq(TENANT), eq(ORDER_ID), eq("SKU-001"), eq("商品A"), eq(2),
                any(BigDecimal.class))).thenReturn(saved);
        when(purchaseOrderService.findPurchaseOrderById(TENANT, ORDER_ID)).thenReturn(reloaded);

        PurchaseOrder result = purchaseApplicationService.addPurchaseOrderItem(TENANT, ORDER_ID, "SKU-001", "商品A",
                2, new BigDecimal("10.00"));

        assertSame(reloaded, result, "出参必须是回读后的单，否则前端拿不到采购项主键，改删按钮无键可用");
        verify(domainEventPublisher).publishEventsFrom(saved);
    }

    @Test
    void searchShouldExpandDateFilterIntoWholeDayBoundaries() {
        when(purchaseOrderService.searchPurchaseOrders(any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(0, 10));

        purchaseApplicationService.searchPurchaseOrders(TENANT, null, null, null, null, PurchaseStatus.PENDING,
                new BigDecimal("100.00"), "2026-01-01", "2026-01-31", 0, 10);

        ArgumentCaptor<PurchaseQuery> captor = ArgumentCaptor.forClass(PurchaseQuery.class);
        verify(purchaseOrderService).searchPurchaseOrders(captor.capture(), eq(0), eq(10));
        PurchaseQuery query = captor.getValue();
        assertEquals(TENANT, query.tenantId());
        assertEquals(PurchaseStatus.PENDING, query.status());
        assertEquals(LocalDate.parse("2026-01-01").atStartOfDay(), query.createdFrom());
        assertEquals(LocalDate.parse("2026-01-31").atTime(LocalTime.MAX), query.createdTo());
    }

    @Test
    void searchShouldRejectMalformedDate() {
        assertThrows(ValidationException.class, () -> purchaseApplicationService.searchPurchaseOrders(TENANT, null,
                null, null, null, null, null, "2026/01/01", null, 0, 10));
    }

    @Test
    void statusChangesShouldPublishAggregateEvents() {
        PurchaseOrder approved = order(PurchaseStatus.APPROVED, BigDecimal.ZERO);
        when(purchaseOrderService.approvePurchaseOrder(TENANT, ORDER_ID, "alice", "同意")).thenReturn(approved);
        PurchaseOrder rejected = order(PurchaseStatus.REJECTED, BigDecimal.ZERO);
        when(purchaseOrderService.rejectPurchaseOrder(TENANT, ORDER_ID, "alice", "价格过高")).thenReturn(rejected);
        PurchaseOrder inTransit = order(PurchaseStatus.IN_TRANSIT, BigDecimal.ZERO);
        when(purchaseOrderService.markPurchaseOrderAsInTransit(TENANT, ORDER_ID)).thenReturn(inTransit);
        PurchaseOrder cancelled = order(PurchaseStatus.CANCELLED, BigDecimal.ZERO);
        when(purchaseOrderService.cancelPurchaseOrder(TENANT, ORDER_ID)).thenReturn(cancelled);

        purchaseApplicationService.approvePurchaseOrder(TENANT, ORDER_ID, "alice", "同意");
        purchaseApplicationService.rejectPurchaseOrder(TENANT, ORDER_ID, "alice", "价格过高");
        purchaseApplicationService.markPurchaseOrderAsInTransit(TENANT, ORDER_ID);
        purchaseApplicationService.cancelPurchaseOrder(TENANT, ORDER_ID);

        // ISS-062：采购/财务聚合此前只落库不发布，判别式是交给 publishEventsFrom 的那个对象
        verify(domainEventPublisher).publishEventsFrom(approved);
        verify(domainEventPublisher).publishEventsFrom(rejected);
        verify(domainEventPublisher).publishEventsFrom(inTransit);
        verify(domainEventPublisher).publishEventsFrom(cancelled);
        verify(warehouseApplicationService, never()).inventoryIn(anyString(), anyString(), anyInt());
    }

    private PurchaseOrder order(PurchaseStatus status, BigDecimal totalAmount, PurchaseOrderItem... items) {
        return PurchaseOrder.builder()
                .purchaseOrderId(ORDER_ID)
                .tenantId(TENANT)
                .supplierId("sup-1")
                .supplierName("供应商A")
                .warehouseId(WAREHOUSE)
                .status(status)
                .totalAmount(totalAmount)
                .items(List.of(items))
                .build();
    }

    private PurchaseOrderItem item(String skuCode, int quantity, String unitPrice) {
        return PurchaseOrderItem.create(skuCode, "商品" + skuCode, quantity, new BigDecimal(unitPrice));
    }

    private Warehouse warehouse(String tenantId) {
        return Warehouse.create(tenantId, "中心仓", "地址", 1000);
    }
}
