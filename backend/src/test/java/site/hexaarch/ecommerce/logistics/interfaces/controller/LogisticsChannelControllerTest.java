package site.hexaarch.ecommerce.logistics.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsChannelService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsChannelController;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsChannelDto;
import site.hexaarch.ecommerce.logistics.interfaces.handler.GlobalExceptionHandler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
 * 物流渠道控制器接口契约测试：覆盖渠道维护（导入按渠道编码匹配）与运费试算。
 *
 * @author kenyon
 */
class LogisticsChannelControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LogisticsChannelService logisticsChannelService;

    @InjectMocks
    private LogisticsChannelController logisticsChannelController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(logisticsChannelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LogisticsChannel buildChannel(String channelId, String channelCode, boolean active) {
        return LogisticsChannel.builder()
                .channelId(channelId)
                .channelCode(channelCode)
                .channelName("顺丰速运")
                .description("顺丰标快")
                .countries(List.of("CN"))
                .basePrice(new BigDecimal("23.00"))
                .pricePerGram(new BigDecimal("0.02"))
                .currency("CNY")
                .active(active)
                .build();
    }

    @Test
    void shouldListChannelsAndFilterActiveOnes() throws Exception {
        when(logisticsChannelService.findAll()).thenReturn(List.of(
                buildChannel("ch-1", "SF", true), buildChannel("ch-2", "DEPRECATED", false)));
        when(logisticsChannelService.getAvailableChannels("CN")).thenReturn(List.of(
                buildChannel("ch-1", "SF", true)));

        mockMvc.perform(get("/api/logistics-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].active").value(false));

        mockMvc.perform(get("/api/logistics-channels").param("onlyActive", "true").param("country", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].channelCode").value("SF"));
    }

    @Test
    void shouldGetChannelByIdAndReturn404WhenMissing() throws Exception {
        when(logisticsChannelService.findById("ch-1")).thenReturn(Optional.of(buildChannel("ch-1", "SF", true)));
        when(logisticsChannelService.findById("ch-404")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logistics-channels/ch-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelId").value("ch-1"))
                .andExpect(jsonPath("$.data.basePrice").value(23.00))
                .andExpect(jsonPath("$.data.countries[0]").value("CN"));

        mockMvc.perform(get("/api/logistics-channels/ch-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldCreateChannelWithGeneratedId() throws Exception {
        when(logisticsChannelService.createLogisticsChannel(any(LogisticsChannel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LogisticsChannelDto request = new LogisticsChannelDto();
        request.setChannelCode(" YTO ");
        request.setChannelName("圆通速递");
        request.setBasePrice(new BigDecimal("8.00"));
        request.setPricePerGram(new BigDecimal("0.005"));

        mockMvc.perform(post("/api/logistics-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.channelName").value("圆通速递"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.currency").value("CNY"));

        ArgumentCaptor<LogisticsChannel> captor = ArgumentCaptor.forClass(LogisticsChannel.class);
        verify(logisticsChannelService).createLogisticsChannel(captor.capture());
        assertEquals("YTO", captor.getValue().getChannelCode());
        assertFalse(captor.getValue().getChannelId().isBlank());
    }

    @Test
    void shouldRejectCreateWhenChannelCodeMissingOrDuplicated() throws Exception {
        LogisticsChannelDto blank = new LogisticsChannelDto();
        blank.setChannelName("无编码渠道");

        mockMvc.perform(post("/api/logistics-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blank)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("渠道编码不能为空"));

        when(logisticsChannelService.createLogisticsChannel(any(LogisticsChannel.class)))
                .thenThrow(new ValidationException("渠道编码已存在: SF"));
        LogisticsChannelDto duplicated = new LogisticsChannelDto();
        duplicated.setChannelCode("SF");
        duplicated.setChannelName("顺丰速运");

        mockMvc.perform(post("/api/logistics-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicated)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("渠道编码已存在: SF"));
    }

    @Test
    void shouldUpdateChannelKeepingUnsetFields() throws Exception {
        when(logisticsChannelService.findById("ch-1")).thenReturn(Optional.of(buildChannel("ch-1", "SF", true)));
        when(logisticsChannelService.updateLogisticsChannel(any(LogisticsChannel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LogisticsChannelDto request = new LogisticsChannelDto();
        request.setDescription("顺丰特快");
        request.setBasePrice(new BigDecimal("25.00"));

        mockMvc.perform(put("/api/logistics-channels/ch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("顺丰特快"))
                .andExpect(jsonPath("$.data.basePrice").value(25.00))
                .andExpect(jsonPath("$.data.channelCode").value("SF"))
                .andExpect(jsonPath("$.data.pricePerGram").value(0.02));
    }

    @Test
    void shouldChangeChannelStatus() throws Exception {
        when(logisticsChannelService.changeChannelStatus("ch-1", false))
                .thenReturn(buildChannel("ch-1", "SF", false));

        mockMvc.perform(put("/api/logistics-channels/ch-1/status").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void shouldBlockDeleteWhenChannelStillReferencedByOrders() throws Exception {
        doThrow(new ValidationException("该渠道下存在物流单，无法删除，请改为停用"))
                .when(logisticsChannelService).deleteLogisticsChannel("ch-1");

        mockMvc.perform(delete("/api/logistics-channels/ch-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verify(logisticsChannelService).deleteLogisticsChannel("ch-1");
        verify(logisticsChannelService, never()).changeChannelStatus(anyString(), eq(true));
    }

    @Test
    void shouldDeleteChannelWhenUnused() throws Exception {
        mockMvc.perform(delete("/api/logistics-channels/ch-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
        verify(logisticsChannelService).deleteLogisticsChannel("ch-2");
    }

    @Test
    void shouldCalculateFeeByWeightInGrams() throws Exception {
        when(logisticsChannelService.calculateFee(eq("ch-1"), anyDouble(), eq("CN")))
                .thenReturn(LogisticsFee.builder()
                        .baseFee(new BigDecimal("23.00"))
                        .weightFee(new BigDecimal("10.00"))
                        .totalFee(new BigDecimal("33.00"))
                        .currency("CNY")
                        .build());

        mockMvc.perform(get("/api/logistics-channels/ch-1/fee")
                        .param("weight", "500")
                        .param("country", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFee").value(33.00))
                .andExpect(jsonPath("$.data.currency").value("CNY"));

        verify(logisticsChannelService).calculateFee("ch-1", 500D, "CN");
    }

    @Test
    void shouldReturn404WhenFeeChannelMissing() throws Exception {
        when(logisticsChannelService.calculateFee(anyString(), anyDouble(), anyString()))
                .thenThrow(new EntityNotFoundException("物流渠道不存在: ch-404"));

        mockMvc.perform(get("/api/logistics-channels/ch-404/fee")
                        .param("weight", "100")
                        .param("country", "CN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenChannelNotSupportCountry() throws Exception {
        when(logisticsChannelService.calculateFee(anyString(), anyDouble(), any()))
                .thenThrow(new ValidationException("物流渠道不支持该国家/地区: US"));

        mockMvc.perform(get("/api/logistics-channels/ch-1/fee")
                        .param("weight", "100")
                        .param("country", "US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }
}
