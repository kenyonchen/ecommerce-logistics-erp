package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LogisticsControllerTest {

    @Mock
    private LogisticsApplicationService logisticsApplicationService;

    @InjectMocks
    private LogisticsController logisticsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(logisticsController).build();
    }

    @Test
    void testCreateLogisticsOrder() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        
        LogisticsOrder mockLogisticsOrder = LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .orderId(orderId)
                .logisticsChannelId(channelId)
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();

        when(logisticsApplicationService.createLogisticsOrder(eq("TEST_TENANT"), eq(orderId), eq(channelId)))
                .thenReturn(mockLogisticsOrder);

        mockMvc.perform(post("/api/logistics")
                .param("orderId", orderId)
                .param("logisticsChannelId", channelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.logisticsOrderId").value(logisticsOrderId));
    }

    @Test
    void testGetLogisticsOrderById() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        
        LogisticsOrder mockLogisticsOrder = LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .orderId(orderId)
                .logisticsChannelId(channelId)
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();

        when(logisticsApplicationService.findLogisticsOrderById(eq(logisticsOrderId))).thenReturn(mockLogisticsOrder);

        mockMvc.perform(get("/api/logistics/" + logisticsOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.logisticsOrderId").value(logisticsOrderId));
    }

    @Test
    void testGetLogisticsOrderByOrderId() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        
        LogisticsOrder mockLogisticsOrder = LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .orderId(orderId)
                .logisticsChannelId(channelId)
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();

        when(logisticsApplicationService.findLogisticsOrderByOrderId(eq(orderId)))
                .thenReturn(List.of(mockLogisticsOrder));

        mockMvc.perform(get("/api/logistics/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testUpdateLogisticsStatus() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        
        LogisticsOrder mockLogisticsOrder = LogisticsOrder.builder()
                .logisticsOrderId(logisticsOrderId)
                .orderId(orderId)
                .logisticsChannelId(channelId)
                .logisticsStatus(LogisticsStatus.IN_TRANSIT)
                .build();

        when(logisticsApplicationService.updateLogisticsStatus(eq(logisticsOrderId), eq(LogisticsStatus.IN_TRANSIT)))
                .thenReturn(mockLogisticsOrder);

        mockMvc.perform(put("/api/logistics/" + logisticsOrderId + "/status")
                .param("status", "IN_TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.logisticsStatus").value("IN_TRANSIT"));
    }
}