package site.hexaarch.ecommerce.logistics.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsController;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipItemDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流控制器接口契约测试。
 *
 * @author kenyon
 */
class LogisticsControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private LogisticsOrderDto buildDto(String logisticsOrderId, String orderId, String channelId,
                                       LogisticsStatus status) {
        LogisticsOrderDto dto = new LogisticsOrderDto();
        dto.setLogisticsOrderId(logisticsOrderId);
        dto.setLogisticsOrderNumber("LOG-0001");
        dto.setOrderId(orderId);
        dto.setLogisticsChannelId(channelId);
        dto.setChannelCode("SF");
        dto.setStatus(status.name());
        dto.setStatusLabel(status.getDescription());
        return dto;
    }

    @Test
    void testCreateLogisticsOrder() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        String logisticsOrderId = UUID.randomUUID().toString();
        LogisticsOrderDto created = buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.PENDING);
        created.setTrackingNumber("SF1234567890");
        when(logisticsApplicationService.createLogisticsOrder(any(CreateLogisticsRequest.class)))
                .thenReturn(created);

        CreateLogisticsRequest request = new CreateLogisticsRequest();
        request.setTenantId("TEST_TENANT");
        request.setOrderId(orderId);
        request.setLogisticsChannelId(channelId);
        request.setTrackingNumber("SF1234567890");

        mockMvc.perform(post("/api/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.logisticsOrderId").value(logisticsOrderId))
                .andExpect(jsonPath("$.data.trackingNumber").value("SF1234567890"));
    }

    @Test
    void testGetLogisticsOrderById() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        when(logisticsApplicationService.getLogisticsOrderDto(eq(logisticsOrderId)))
                .thenReturn(buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.PENDING));

        mockMvc.perform(get("/api/logistics/" + logisticsOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.logisticsOrderId").value(logisticsOrderId));
    }

    @Test
    void testGetLogisticsOrderByOrderId() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        when(logisticsApplicationService.findLogisticsOrderByOrderId(eq(orderId)))
                .thenReturn(List.of(buildDto(UUID.randomUUID().toString(), orderId, channelId,
                        LogisticsStatus.PENDING)));

        mockMvc.perform(get("/api/logistics/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testSearchLogisticsOrders() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        PageResult<LogisticsOrderDto> page = new PageResult<>(
                List.of(buildDto(UUID.randomUUID().toString(), orderId, channelId, LogisticsStatus.IN_TRANSIT)),
                1L, 0, 20);
        when(logisticsApplicationService.search(anyString(), isNull(), isNull(), isNull(), eq("IN_TRANSIT"),
                isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/logistics")
                        .param("tenantId", "TEST_TENANT")
                        .param("status", "IN_TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("IN_TRANSIT"));
    }

    @Test
    void testUpdateLogisticsStatus() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        when(logisticsApplicationService.updateLogisticsStatus(eq(logisticsOrderId), eq(LogisticsStatus.IN_TRANSIT),
                anyString())).thenReturn(buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.IN_TRANSIT));

        mockMvc.perform(put("/api/logistics/" + logisticsOrderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\",\"description\":\"运输中\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    void testShipAndCancelAndLabel() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String channelId = UUID.randomUUID().toString();
        when(logisticsApplicationService.ship(eq(logisticsOrderId), isNull(), eq("SF9999")))
                .thenReturn(buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.COLLECTED));
        when(logisticsApplicationService.cancel(eq(logisticsOrderId)))
                .thenReturn(buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.CANCELLED));
        when(logisticsApplicationService.generateLabel(eq(logisticsOrderId)))
                .thenReturn(buildDto(logisticsOrderId, orderId, channelId, LogisticsStatus.LABEL_GENERATED));

        mockMvc.perform(post("/api/logistics/" + logisticsOrderId + "/ship").param("trackingNumber", "SF9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COLLECTED"));
        mockMvc.perform(post("/api/logistics/" + logisticsOrderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(post("/api/logistics/" + logisticsOrderId + "/label"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LABEL_GENERATED"));
    }

    @Test
    void testBatchShip() throws Exception {
        BatchShipResultDto result = BatchShipResultDto.of(2);
        result.recordSuccess("logistics-1");
        result.recordFailure(2, "ORD-404", "订单不存在，订单号=ORD-404");
        result.buildMessage();
        when(logisticsApplicationService.batchShip(any(BatchShipRequest.class))).thenReturn(result);

        BatchShipRequest request = new BatchShipRequest();
        request.setTenantId("TEST_TENANT");
        request.setChannelCode("SF");
        BatchShipItemDto item = new BatchShipItemDto();
        item.setOrderNumber("ORD-001");
        item.setTrackingNumber("SF1234567890");
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/logistics/batch-ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.failures[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data.failures[0].type").value("FAILURE"))
                .andExpect(jsonPath("$.data.failures[0].reason").value("订单不存在，订单号=ORD-404"));
    }

    @Test
    void testDeleteLogisticsOrder() throws Exception {
        String logisticsOrderId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/logistics/" + logisticsOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }
}
