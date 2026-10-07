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
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsImportError;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsImportController;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流导入控制器接口契约测试：覆盖数据获取预览、字段映射读写、批量导入与结果反馈。
 *
 * @author kenyon
 */
class LogisticsImportControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LogisticsImportApplicationService logisticsImportApplicationService;

    @Mock
    private site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser excelSourceParser;

    @InjectMocks
    private LogisticsImportController logisticsImportController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(logisticsImportController).build();
    }

    @Test
    void shouldExposeTargetFieldsAndStatuses() throws Exception {
        when(logisticsImportApplicationService.targetFields()).thenReturn(List.of(
                new TargetFieldDto("EXTERNAL_LOGISTICS_ID", "平台物流单号", Boolean.TRUE)));
        when(logisticsImportApplicationService.logisticsStatuses()).thenReturn(List.of(
                new TargetFieldDto("IN_TRANSIT", "运输中", Boolean.FALSE)));
        when(logisticsImportApplicationService.transforms()).thenReturn(List.of(
                new TargetFieldDto("NONE", "不转换", Boolean.FALSE)));

        mockMvc.perform(get("/api/logistics/import/target-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("EXTERNAL_LOGISTICS_ID"))
                .andExpect(jsonPath("$.data[0].required").value(true));
        mockMvc.perform(get("/api/logistics/import/logistics-statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("运输中"));
        mockMvc.perform(get("/api/logistics/import/transforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("NONE"));
    }

    @Test
    void shouldReadAndSaveFieldMappings() throws Exception {
        FieldMappingDto mapping = new FieldMappingDto();
        mapping.setSourceField("shipment-id");
        mapping.setTargetField("EXTERNAL_LOGISTICS_ID");
        mapping.setTransform("NONE");
        mapping.setEnabled(Boolean.TRUE);
        when(logisticsImportApplicationService.getMappings(anyString(), eq("AMAZON"))).thenReturn(List.of(mapping));
        when(logisticsImportApplicationService.saveMappings(any(FieldMappingSaveRequest.class)))
                .thenReturn(List.of(mapping));

        mockMvc.perform(get("/api/logistics/import/field-mappings")
                        .param("tenantId", "tenant-001")
                        .param("platform", "AMAZON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceField").value("shipment-id"))
                .andExpect(jsonPath("$.data[0].targetField").value("EXTERNAL_LOGISTICS_ID"));

        FieldMappingSaveRequest saveRequest = new FieldMappingSaveRequest();
        saveRequest.setPlatform("AMAZON");
        saveRequest.setMappings(List.of(mapping));
        mockMvc.perform(put("/api/logistics/import/field-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    void shouldPreviewPlatformLogisticsRows() throws Exception {
        LogisticsImportPreviewResponse response = new LogisticsImportPreviewResponse();
        response.setPlatform("AMAZON");
        response.setSourceFields(List.of("shipment-id", "tracking-number"));
        response.setTotalCount(1);
        LogisticsImportErrorDto error = new LogisticsImportErrorDto();
        error.setRowNumber(2);
        error.setMessage("平台物流单号为空，无法确定导入幂等键");
        response.setRowErrors(List.of(error));
        response.setWarnings(List.of("存在 1 条字段级错误，导入时将跳过对应行"));
        when(logisticsImportApplicationService.preview(any(LogisticsImportPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/logistics/import/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"AMAZON\",\"useSampleData\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("AMAZON"))
                .andExpect(jsonPath("$.data.rowErrors[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data.warnings.length()").value(1));
    }

    @Test
    void shouldReturnJobResultAfterImport() throws Exception {
        LogisticsImportJobDto job = new LogisticsImportJobDto();
        job.setJobId("job-1");
        job.setPlatform("AMAZON");
        job.setStatus("PARTIAL_SUCCESS");
        job.setStatusLabel("部分成功");
        job.setTotalCount(3);
        job.setSuccessCount(2);
        job.setSkippedCount(1);
        job.setFailureCount(0);
        when(logisticsImportApplicationService.importLogistics(any(LogisticsImportRequest.class))).thenReturn(job);

        LogisticsImportRequest request = new LogisticsImportRequest();
        request.setPlatform("AMAZON");
        mockMvc.perform(post("/api/logistics/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("PARTIAL_SUCCESS"))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.skippedCount").value(1));
    }

    @Test
    void shouldListJobsAndFetchTemplate() throws Exception {
        when(logisticsImportApplicationService.listJobs(anyString(), eq(0), eq(10)))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 10));
        when(logisticsImportApplicationService.csvTemplate()).thenReturn("externalLogisticsId,trackingNumber\n");

        mockMvc.perform(get("/api/logistics/import/jobs").param("tenantId", "default-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/logistics/import/template"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("externalLogisticsId")));
    }

    @Test
    void shouldTriggerPlatformSync() throws Exception {
        LogisticsImportJobDto job = new LogisticsImportJobDto();
        job.setJobId("job-2");
        job.setStatus("SUCCESS");
        job.setSuccessCount(4);
        when(logisticsImportApplicationService.syncFromPlatform(anyString(), anyString(), any(), any(), any(), any(),
                any(), any())).thenReturn(job);

        mockMvc.perform(post("/api/logistics/import/sync")
                        .param("integrationId", "0d1f6a7c-0000-0000-0000-000000000001")
                        .param("tenantId", "default-tenant")
                        .param("useSampleData", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-2"))
                .andExpect(jsonPath("$.data.successCount").value(4));
        verify(logisticsImportApplicationService).syncFromPlatform(eq("0d1f6a7c-0000-0000-0000-000000000001"),
                anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldMapImportErrorToUnifiedResult() throws Exception {
        when(logisticsImportApplicationService.importLogistics(any(LogisticsImportRequest.class)))
                .thenThrow(new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                        "批量导入数据为空"));

        mockMvc.perform(post("/api/logistics/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("批量导入数据为空"));
    }

    @Test
    void shouldKeepErrorDomainObjectOutOfDtoLayer() {
        // 领域错误对象与对外DTO字段需保持一致，避免前端读取不到错误明细
        LogisticsImportError error = LogisticsImportError.of(1, "AMAZON-SHIP-001", "AMAZON-ORD-001",
                "STATUS", "状态无法识别");
        LogisticsImportErrorDto dto = new LogisticsImportErrorDto();
        dto.setRowNumber(error.getRowNumber());
        dto.setExternalLogisticsId(error.getExternalLogisticsId());
        dto.setOrderNumber(error.getOrderNumber());
        dto.setField(error.getField());
        dto.setMessage(error.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals("状态无法识别", dto.getMessage());
    }
}
