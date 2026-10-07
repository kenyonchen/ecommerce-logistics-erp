package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsImportJob;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsImportError;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.ImportedLogisticsRow;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 平台物流导入链路核心逻辑测试：
 * 数据解析 -> 字段识别 -> 行级映射（状态/时间/轨迹） -> 必填校验 -> 任务结果反馈。
 *
 * @author kenyon
 */
class LogisticsImportPipelineTest {

    @Mock
    private LogisticsFieldMappingRepository fieldMappingRepository;

    private LogisticsFieldMappingService fieldMappingService;
    private ImportSourceParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fieldMappingService = new LogisticsFieldMappingService(fieldMappingRepository);
        parser = new ImportSourceParser();
    }

    @Test
    void shouldParseCsvAndMapStandardLogisticsFields() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String csv = "externalLogisticsId,trackingNumber,externalOrderId,channelCode,carrier,status,"
                + "shippedAt,estimatedArrivalAt,deliveredAt,totalFee,currency,trackingUrl,recipient,phone\n"
                + "SF-SHIP-001,SF1234567890,AMAZON-ORD-001,SF,顺丰速运,Delivered,2026-09-20 10:00:00,"
                + "2026-09-25 10:00:00,2026-09-24 18:30:00,23.50,CNY,https://track.example.com/SF1234567890,"
                + "张三,13800138000\n";

        List<Map<String, Object>> rawRows = parser.parse(csv);
        assertEquals(1, rawRows.size());

        List<String> sourceFields = fieldMappingService.detectSourceFields(rawRows);
        assertTrue(sourceFields.contains("externalLogisticsId"));

        var mappings = fieldMappingService.resolveMappings("default-tenant", "FILE", sourceFields);
        assertFalse(mappings.isEmpty());

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        ImportedLogisticsRow row = result.row();

        assertEquals("SF-SHIP-001", row.externalLogisticsId());
        assertEquals("SF1234567890", row.trackingNumber());
        assertEquals("AMAZON-ORD-001", row.externalOrderId());
        assertEquals("SF", row.channelCode());
        assertEquals("顺丰速运", row.carrier());
        assertEquals(LogisticsStatus.DELIVERED, row.status());
        assertEquals(0, new BigDecimal("23.5").compareTo(row.totalFee()));
        assertNotNull(row.shippedAt());
        assertNotNull(row.deliveredAt());
        assertTrue(result.errors().isEmpty(), "标准CSV不应产生行级错误");
    }

    @Test
    void shouldAutoDetectAmazonKebabCaseFieldsAndJsonStringTraces() throws Exception {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String eventHistory = "[{\"time\":\"2026-09-20 10:00:00\",\"status\":\"Shipped\","
                + "\"description\":\"已揽收\",\"location\":\"深圳转运中心\"}]";
        String json = "[{\"shipment-id\":\"AMAZON-SHIP-001\",\"amazon-order-id\":\"AMAZON-ORD-001\","
                + "\"tracking-number\":\"A001-TN-1\",\"carrier-name\":\"顺丰速运\",\"shipping-status\":\"InTransit\","
                + "\"ship-date\":\"2026-09-20 10:00:00\",\"estimated-delivery-date\":\"2026-09-25 10:00:00\","
                + "\"shipping-url\":\"https://track.example.com/AMAZON/001\",\"recipient-name\":\"张三1\","
                + "\"postal-code\":\"518000\",\"shipping-fee\":13.5,\"event-history\":"
                + com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(eventHistory)
                + "}]";
        List<Map<String, Object>> rawRows = parser.parse(json);
        var sourceFields = fieldMappingService.detectSourceFields(rawRows);
        var mappings = fieldMappingService.resolveMappings("default-tenant", "AMAZON", sourceFields);

        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == LogisticsImportTargetField.EXTERNAL_LOGISTICS_ID));
        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == LogisticsImportTargetField.EXTERNAL_ORDER_ID));
        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == LogisticsImportTargetField.TRACES));

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        ImportedLogisticsRow row = result.row();
        assertEquals("AMAZON-SHIP-001", row.externalLogisticsId());
        assertEquals("AMAZON-ORD-001", row.externalOrderId());
        assertEquals("顺丰速运", row.carrier());
        assertEquals(LogisticsStatus.IN_TRANSIT, row.status());
        assertEquals("https://track.example.com/AMAZON/001", row.trackingUrl());
        assertEquals(0, new BigDecimal("13.5").compareTo(row.totalFee()));
        assertEquals(1, row.traces().size(), "JSON字符串形式的轨迹应被解析");
        assertEquals("已揽收", row.traces().get(0).description());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldDeriveStatusFromLastTraceWhenStatusAbsent() {
        Map<String, Object> rawRow = new java.util.LinkedHashMap<>();
        rawRow.put("externalLogisticsId", "JD-SHIP-002");
        rawRow.put("externalOrderId", "JD-ORD-002");
        rawRow.put("traces", List.of(
                Map.of("time", "2026-09-20 10:00:00", "status", "Shipped", "description", "已发货"),
                Map.of("time", "2026-09-24 09:00:00", "status", "Delivered", "description", "已签收")));

        var mappings = fieldMappingService.autoDetect(fieldMappingService.detectSourceFields(List.of(rawRow)));
        var result = fieldMappingService.mapRow(mappings, rawRow, 1);

        assertEquals(2, result.row().traces().size());
        assertEquals(LogisticsStatus.DELIVERED, result.row().status(), "无状态字段时应由最后一条轨迹推导");
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldCollectRowErrorsWhenRequiredFieldsMissing() {
        Map<String, Object> rawRow = new java.util.LinkedHashMap<>();
        rawRow.put("carrier", "顺丰速运");
        rawRow.put("totalFee", "-12.5");
        var mappings = fieldMappingService.autoDetect(fieldMappingService.detectSourceFields(List.of(rawRow)));
        var result = fieldMappingService.mapRow(mappings, rawRow, 3);

        assertTrue(result.errors().size() >= 3, "缺少平台物流单号、订单标识且费用为负时应产生错误");
        assertTrue(result.errors().stream().anyMatch(error ->
                LogisticsImportTargetField.EXTERNAL_LOGISTICS_ID.name().equals(error.getField())));
        assertTrue(result.errors().stream().anyMatch(error ->
                LogisticsImportTargetField.EXTERNAL_ORDER_ID.name().equals(error.getField())));
        assertTrue(result.errors().stream().anyMatch(error ->
                LogisticsImportTargetField.TOTAL_FEE.name().equals(error.getField())));
    }

    @Test
    void shouldParseMultipleDateFormatsAndUnknownStatus() {
        assertEquals(LogisticsStatus.COLLECTED, fieldMappingService.toLogisticsStatus("已揽收"));
        assertEquals(LogisticsStatus.FAILED, fieldMappingService.toLogisticsStatus("exception"));
        assertEquals(LogisticsStatus.PENDING, fieldMappingService.toLogisticsStatus("无法识别的状态"));

        assertNotNull(fieldMappingService.toDateTime("2026/09/20 10:00:00"));
        assertNotNull(fieldMappingService.toDateTime("20260920100000"));
        assertNotNull(fieldMappingService.toDateTime("2026-09-20"));
        assertNotNull(fieldMappingService.toDateTime("2026-01-25T10:20:30Z"));
    }

    @Test
    void shouldRecordSuccessSkipFailureAndCompleteJob() {
        LogisticsImportJob job = LogisticsImportJob.create("default-tenant", "AMAZON", null, "logistics.csv");
        job.start(4);
        job.recordSuccess("lo-1", false);
        job.recordSuccess("lo-2", true);
        job.recordSkipped(LogisticsImportError.of(3, "AMAZON-SHIP-003", null,
                LogisticsImportTargetField.STATUS.name(), "物流单已处于终态，不再回退更新"));
        job.recordFailure(LogisticsImportError.of(4, null, "AMAZON-ORD-004",
                LogisticsImportTargetField.EXTERNAL_LOGISTICS_ID.name(), "平台物流单号为空"));
        job.complete();

        assertEquals(4, job.getTotalCount());
        assertEquals(2, job.getSuccessCount());
        assertEquals(1, job.getSkippedCount());
        assertEquals(1, job.getFailureCount());
        assertNotNull(job.getFinishedAt());
        assertTrue(job.hasErrors());
        assertTrue(job.getCreatedLogisticsOrderIds().contains("lo-1"));
        assertTrue(job.getUpdatedLogisticsOrderIds().contains("lo-2"));
    }
}
