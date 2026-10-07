package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.interfaces.controller.purchase.PurchaseController;
import site.hexaarch.ecommerce.logistics.interfaces.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 采购控制器契约测试：JSON 入参、DTO 出参、租户解析与状态动作端点。
 *
 * @author kenyon
 */
class PurchaseControllerTest {

    private static final String TENANT = "TEST_TENANT";

    @Mock
    private PurchaseApplicationService purchaseApplicationService;

    @InjectMocks
    private PurchaseController purchaseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 审批人取自 SecurityContextHolder，同类可能被其他用例污染，先复位再断言
        SecurityContextHolder.clearContext();
        // 注册全局异常处理：控制器不再自行 try/catch，业务失败必须映射成 4xx 而不是 HTTP 200 + code=500
        mockMvc = MockMvcBuilders.standaloneSetup(purchaseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreatePurchaseOrderWithItems() throws Exception {
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        PurchaseOrder mockPurchaseOrder = sampleOrder(PurchaseStatus.PENDING);

        when(purchaseApplicationService.createPurchaseOrder(eq(TENANT), eq(supplierId), eq("Supplier ABC"),
                eq(warehouseId), any())).thenReturn(mockPurchaseOrder);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT + "\",\"supplierId\":\"" + supplierId
                                + "\",\"supplierName\":\"Supplier ABC\",\"warehouseId\":\"" + warehouseId
                                + "\",\"items\":[{\"skuCode\":\"SKU001\",\"productName\":\"Product Name\","
                                + "\"quantity\":10,\"unitPrice\":100.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.purchaseOrderId").value(mockPurchaseOrder.getPurchaseOrderId()))
                // 聚合不再外露：内部待发事件队列不得出现在响应里（ISS-063）
                .andExpect(jsonPath("$.data.domainEvents").doesNotExist())
                // 待处理单可批准/拒绝/取消，前端按此渲染动作按钮
                .andExpect(jsonPath("$.data.allowedNextStatuses").value(hasSize(3)))
                .andExpect(jsonPath("$.data.allowedNextStatuses[0]").value("APPROVED"))
                .andExpect(jsonPath("$.data.allowedNextStatuses[1]").value("REJECTED"))
                .andExpect(jsonPath("$.data.allowedNextStatuses[2]").value("CANCELLED"));
    }

    @Test
    void testCreatePurchaseOrderDefaultsToDefaultTenant() throws Exception {
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        PurchaseOrder mockPurchaseOrder = sampleOrder(UUID.randomUUID().toString(), "default-tenant", PurchaseStatus.PENDING);

        when(purchaseApplicationService.createPurchaseOrder(eq("default-tenant"), eq(supplierId),
                eq("Supplier ABC"), eq(warehouseId), any())).thenReturn(mockPurchaseOrder);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supplierId + "\",\"supplierName\":\"Supplier ABC\","
                                + "\"warehouseId\":\"" + warehouseId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("default-tenant"));
    }

    @Test
    void testSearchPurchaseOrdersPassesFiltersAndPaging() throws Exception {
        PurchaseOrder mockPurchaseOrder = sampleOrder(PurchaseStatus.DELIVERED);
        when(purchaseApplicationService.searchPurchaseOrders(eq(TENANT), eq("PO-1"), eq("SUP-1"), isNull(),
                isNull(), eq(PurchaseStatus.DELIVERED), any(), eq("2026-01-01"), eq("2026-01-31"), eq(1), eq(20)))
                .thenReturn(new PageResult<>(List.of(mockPurchaseOrder), 1L, 1, 20));

        mockMvc.perform(get("/api/purchases")
                        .param("tenantId", TENANT)
                        .param("purchaseOrderId", "PO-1")
                        .param("supplierId", "SUP-1")
                        .param("status", "DELIVERED")
                        .param("minTotalAmount", "100.00")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("DELIVERED"));
    }

    @Test
    void testSearchUsesPageOneByDefault() throws Exception {
        when(purchaseApplicationService.searchPurchaseOrders(eq("default-tenant"), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(PageResult.empty(0, 10));

        mockMvc.perform(get("/api/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        verify(purchaseApplicationService, never()).findPurchaseOrderById(any(), any());
    }

    @Test
    void testStatusesDictionary() throws Exception {
        mockMvc.perform(get("/api/purchases/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)))
                .andExpect(jsonPath("$.data[0].code").value("PENDING"))
                .andExpect(jsonPath("$.data[0].label").value("待处理"));
    }

    @Test
    void testFindPurchaseOrderById() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.findPurchaseOrderById(TENANT, purchaseOrderId))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.PENDING));

        mockMvc.perform(get("/api/purchases/" + purchaseOrderId).param("tenantId", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseOrderId").value(purchaseOrderId));
    }

    @Test
    void testAddPurchaseOrderItem() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.addPurchaseOrderItem(eq(TENANT), eq(purchaseOrderId), eq("SKU001"),
                eq("Product Name"), eq(10), any(BigDecimal.class)))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.PENDING));

        mockMvc.perform(post("/api/purchases/" + purchaseOrderId + "/items")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"SKU001\",\"productName\":\"Product Name\",\"quantity\":10,"
                                + "\"unitPrice\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseOrderId").value(purchaseOrderId));
    }

    @Test
    void testUpdatePurchaseOrderItem() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.updatePurchaseOrderItem(eq(TENANT), eq(purchaseOrderId), eq("7"),
                eq(20), any(BigDecimal.class))).thenReturn(sampleOrder(PurchaseStatus.PENDING));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/items/7")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20,\"unitPrice\":88.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testRemovePurchaseOrderItem() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.removePurchaseOrderItem(TENANT, purchaseOrderId, "7"))
                .thenReturn(sampleOrder(PurchaseStatus.PENDING));

        mockMvc.perform(delete("/api/purchases/" + purchaseOrderId + "/items/7").param("tenantId", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    void testApproveFallsBackToSystemWithoutAuthenticatedPrincipal() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.approvePurchaseOrder(eq(TENANT), eq(purchaseOrderId), any(), any()))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.APPROVED));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/approve")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"预算内，同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 无认证主体（standaloneSetup）时审批人兜底为 SYSTEM，不允许审批人留空
        verify(purchaseApplicationService).approvePurchaseOrder(TENANT, purchaseOrderId, "SYSTEM", "预算内，同意");
    }

    @Test
    void testApproveUsesLoginAccountWhenApproverOmitted() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "secret", List.of()));
        when(purchaseApplicationService.approvePurchaseOrder(eq(TENANT), eq(purchaseOrderId), any(), any()))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.APPROVED));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/approve")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"预算内，同意\"}"))
                .andExpect(status().isOk())
                // 审批人取登录账号，落库留痕（BR-06）
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(purchaseApplicationService).approvePurchaseOrder(TENANT, purchaseOrderId, "alice", "预算内，同意");
    }

    @Test
    void testApproveKeepsExplicitApproverFromRequestBody() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.approvePurchaseOrder(eq(TENANT), eq(purchaseOrderId), any(), any()))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.APPROVED));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/approve")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"采购经理-王\",\"comment\":\"同意\"}"))
                .andExpect(status().isOk());

        verify(purchaseApplicationService).approvePurchaseOrder(TENANT, purchaseOrderId, "采购经理-王", "同意");
    }

    @Test
    void testRejectWithoutReasonReturnsHttp400() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/reject")
                        .param("tenantId", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("拒绝采购单必须填写拒绝原因"));

        verify(purchaseApplicationService, never()).rejectPurchaseOrder(any(), any(), any(), any());
    }

    @Test
    void testMarkPurchaseOrderAsInTransit() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.markPurchaseOrderAsInTransit(TENANT, purchaseOrderId))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.IN_TRANSIT));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/in-transit").param("tenantId", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                // 运输中只能走到货，不能取消
                .andExpect(jsonPath("$.data.allowedNextStatuses").value(hasSize(1)))
                .andExpect(jsonPath("$.data.allowedNextStatuses[0]").value("DELIVERED"));
    }

    @Test
    void testMarkPurchaseOrderAsDelivered() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.markPurchaseOrderAsDelivered(TENANT, purchaseOrderId))
                .thenReturn(sampleOrder(purchaseOrderId, TENANT, PurchaseStatus.DELIVERED));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/delivered").param("tenantId", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.statusLabel").value("已送达"))
                .andExpect(jsonPath("$.data.allowedNextStatuses").value(hasSize(0)));
    }

    @Test
    void testCancelPurchaseOrder() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.cancelPurchaseOrder(TENANT, purchaseOrderId))
                .thenReturn(sampleOrder(PurchaseStatus.CANCELLED));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/cancel").param("tenantId", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void testIllegalStateTransitionReturnsHttp400() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        when(purchaseApplicationService.markPurchaseOrderAsInTransit(TENANT, purchaseOrderId))
                .thenThrow(new IllegalStateException("采购单不允许从「已送达」变更为「运输中」"));

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/in-transit").param("tenantId", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
    }

    private PurchaseOrder sampleOrder(PurchaseStatus status) {
        return sampleOrder(UUID.randomUUID().toString(), TENANT, status);
    }

    private PurchaseOrder sampleOrder(String purchaseOrderId, String tenantId, PurchaseStatus status) {
        return PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .tenantId(tenantId)
                .supplierId(UUID.randomUUID().toString())
                .supplierName("Supplier ABC")
                .warehouseId(UUID.randomUUID().toString())
                .status(status)
                .totalAmount(new BigDecimal("1000.00"))
                .build();
    }
}
