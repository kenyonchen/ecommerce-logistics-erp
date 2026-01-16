package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WarehouseControllerTest {

    @Mock
    private WarehouseApplicationService warehouseApplicationService;

    @InjectMocks
    private WarehouseController warehouseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(warehouseController).build();
    }

    @Test
    void testCreateWarehouse() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        
        Warehouse mockWarehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .warehouseCode("MW001")
                .address("123 Main St")
                .capacity(1000)
                .active(true)
                .build();

        when(warehouseApplicationService.createWarehouse(eq("123"), eq("Main Warehouse"), eq("MW001"), eq("123 Main St"),  eq(1000)))
                .thenReturn(mockWarehouse);

        mockMvc.perform(post("/api/warehouses")
                .param("warehouseName", "Main Warehouse")
                .param("warehouseCode", "MW001")
                .param("address", "123 Main St")
                .param("capacity", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.warehouseId").value(warehouseId));
    }

    @Test
    void testGetWarehouseById() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        
        Warehouse mockWarehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .warehouseCode("MW001")
                .address("123 Main St")
                .capacity(1000)
                .active(true)
                .build();

        when(warehouseApplicationService.findWarehouseById(eq(warehouseId))).thenReturn(mockWarehouse);

        mockMvc.perform(get("/api/warehouses/" + warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.warehouseId").value(warehouseId));
    }

    @Test
    void testGetAllWarehouses() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        
        Warehouse mockWarehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .warehouseCode("MW001")
                .address("123 Main St")
                .capacity(1000)
                .active(true)
                .build();

        when(warehouseApplicationService.findAllWarehouses())
                .thenReturn(List.of(mockWarehouse));

        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testGetInventoryByWarehouse() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        InventoryRecord mockInventory = InventoryRecord.builder()
                .inventoryId(productId)
                .warehouseId(warehouseId)
                .sku("SKU001")
                .quantity(100)
                .inventoryStatus(InventoryStatus.NORMAL)
                .build();

        when(warehouseApplicationService.findInventoryByWarehouse(eq(warehouseId)))
                .thenReturn(List.of(mockInventory));

        mockMvc.perform(get("/api/warehouses/" + warehouseId + "/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testCountInventory() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        InventoryRecord mockInventory = InventoryRecord.builder()
                .inventoryId(productId)
                .warehouseId(warehouseId)
                .sku("SKU001")
                .quantity(150)
                .inventoryStatus(InventoryStatus.NORMAL)
                .build();

        when(warehouseApplicationService.countInventory(eq(warehouseId), eq(productId), eq(150)))
                .thenReturn(mockInventory);

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/inventory/count")
                .param("productId", productId)
                .param("actualQuantity", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.quantity").value(150));
    }

    @Test
    void testMoveInventory() throws Exception {
        String warehouseId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        InventoryMovement movement = InventoryMovement.builder()
                .movementId(UUID.randomUUID().toString())
                .warehouseId(warehouseId)
                .productId(productId)
                .sku("SKU001")
                .quantity(50)
                .movementType(InventoryMovement.MovementType.INBOUND)
                .reason("Restocking")
                .movedAt(LocalDateTime.now())
                .build();
        
        InventoryRecord mockInventory = InventoryRecord.builder()
                .inventoryId(productId)
                .warehouseId(warehouseId)
                .sku("SKU001")
                .quantity(150)
                .inventoryStatus(InventoryStatus.NORMAL)
                .build();

        when(warehouseApplicationService.moveInventory(eq(warehouseId), any(InventoryMovement.class)))
                .thenReturn(mockInventory);

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/inventory/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"movementId\":\"" + UUID.randomUUID() + "\",\"warehouseId\":\"" + warehouseId + "\",\"productId\":\"" + productId + "\",\"sku\":\"SKU001\",\"quantity\":50,\"movementType\":\"INBOUND\",\"reason\":\"Restocking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.quantity").value(150));
    }
}