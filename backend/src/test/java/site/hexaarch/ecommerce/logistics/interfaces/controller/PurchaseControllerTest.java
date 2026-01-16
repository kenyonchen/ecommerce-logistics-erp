package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PurchaseControllerTest {

    @Mock
    private PurchaseApplicationService purchaseApplicationService;

    @InjectMocks
    private PurchaseController purchaseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(purchaseController).build();
    }

    @Test
    void testCreatePurchaseOrder() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.PENDING)
                .build();

        when(purchaseApplicationService.createPurchaseOrder(eq("TEST_TENANT"), eq(supplierId), eq("Supplier ABC"), eq(warehouseId)))
                .thenReturn(mockPurchaseOrder);

        mockMvc.perform(post("/api/purchases")
                .param("supplierId", supplierId)
                .param("supplierName", "Supplier ABC")
                .param("warehouseId", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.purchaseOrderId").value(purchaseOrderId));
    }

    @Test
    void testFindPurchaseOrderById() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.PENDING)
                .build();

        when(purchaseApplicationService.findPurchaseOrderById(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(get("/api/purchases/" + purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.purchaseOrderId").value(purchaseOrderId));
    }

    @Test
    void testFindPurchaseOrders() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.PENDING)
                .build();

        when(purchaseApplicationService.findPurchaseOrdersByStatus(eq(PurchaseStatus.PENDING)))
                .thenReturn(List.of(mockPurchaseOrder));

        mockMvc.perform(get("/api/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testAddPurchaseOrderItem() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.PENDING)
                .build();

        when(purchaseApplicationService.addPurchaseOrderItem(eq(purchaseOrderId), eq("SKU001"), eq("Product Name"), eq(10), any(BigDecimal.class)))
                .thenReturn(mockPurchaseOrder);

        mockMvc.perform(post("/api/purchases/" + purchaseOrderId + "/items")
                .param("skuCode", "SKU001")
                .param("productName", "Product Name")
                .param("quantity", "10")
                .param("unitPrice", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.purchaseOrderId").value(purchaseOrderId));
    }

    @Test
    void testApprovePurchaseOrder() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.APPROVED)
                .build();

        when(purchaseApplicationService.approvePurchaseOrder(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void testRejectPurchaseOrder() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.REJECTED)
                .build();

        when(purchaseApplicationService.rejectPurchaseOrder(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void testMarkPurchaseOrderAsInTransit() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.IN_TRANSIT)
                .build();

        when(purchaseApplicationService.markPurchaseOrderAsInTransit(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/in-transit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    void testMarkPurchaseOrderAsDelivered() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.DELIVERED)
                .build();

        when(purchaseApplicationService.markPurchaseOrderAsDelivered(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void testCancelPurchaseOrder() throws Exception {
        String purchaseOrderId = UUID.randomUUID().toString();
        String supplierId = UUID.randomUUID().toString();
        String warehouseId = UUID.randomUUID().toString();
        
        PurchaseOrder mockPurchaseOrder = PurchaseOrder.builder()
                .purchaseOrderId(purchaseOrderId)
                .supplierId(supplierId)
                .supplierName("Supplier ABC")
                .warehouseId(warehouseId)
                .status(PurchaseStatus.CANCELLED)
                .build();

        when(purchaseApplicationService.cancelPurchaseOrder(eq(purchaseOrderId))).thenReturn(mockPurchaseOrder);

        mockMvc.perform(put("/api/purchases/" + purchaseOrderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}