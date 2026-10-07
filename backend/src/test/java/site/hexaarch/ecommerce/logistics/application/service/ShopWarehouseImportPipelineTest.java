package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouseImportJob;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.ShopWarehouseFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ImportedShopWarehouseRow;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ShopWarehouseImportTargetField;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 店铺仓库导入链路核心逻辑测试：文件解析 -> 字段识别 -> 行级映射与错误收集 -> 任务结果反馈。
 */
class ShopWarehouseImportPipelineTest {

    @Mock
    private ShopWarehouseFieldMappingRepository fieldMappingRepository;

    private ShopWarehouseFieldMappingService fieldMappingService;
    private ImportSourceParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fieldMappingService = new ShopWarehouseFieldMappingService(fieldMappingRepository);
        parser = new ImportSourceParser();
    }

    @Test
    void shouldParseCsvAndMapStandardizedFields() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String csv = "shopCode,shopName,platformWarehouseId,platformWarehouseName,warehouseCode,address,isDefault,active\n"
                + "AMZ-STORE-01,亚马逊店铺1,AMZ-WH-01,Amazon FBA 仓1,WH-001,美国加州1号仓,true,true\n";

        List<Map<String, Object>> rawRows = parser.parse(csv);
        assertEquals(1, rawRows.size());
        assertEquals("AMZ-STORE-01", rawRows.get(0).get("shopCode"));

        List<String> sourceFields = fieldMappingService.detectSourceFields(rawRows);
        assertTrue(sourceFields.contains("shopCode"));

        var mappings = fieldMappingService.resolveMappings("default-tenant", "FILE", sourceFields);
        assertFalse(mappings.isEmpty());

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        ImportedShopWarehouseRow row = result.row();

        assertEquals("AMZ-STORE-01", row.shopCode());
        assertEquals("亚马逊店铺1", row.shopName());
        assertEquals("AMZ-WH-01", row.platformWarehouseId());
        assertEquals("WH-001", row.warehouseCode());
        assertEquals(Boolean.TRUE, row.isDefault());
        assertEquals(Boolean.TRUE, row.active());
        assertTrue(result.errors().isEmpty(), "标准CSV不应产生行级错误");
    }

    @Test
    void shouldAutoDetectPlatformAliases() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String json = """
                [
                  {"shop_code":"AMZ-STORE-1","shop_name":"亚马逊店铺","warehouse_id":"AMZ-WH-1",
                   "warehouse_name":"Amazon FBA 仓","warehouse_code":"WH-001","address":"美国加州",
                   "is_default":"true","status":"Active"}
                ]
                """;
        List<Map<String, Object>> rawRows = parser.parse(json);
        var sourceFields = fieldMappingService.detectSourceFields(rawRows);
        var mappings = fieldMappingService.resolveMappings("default-tenant", "AMAZON", sourceFields);

        assertTrue(mappings.stream().anyMatch(m -> m.getTargetField() == ShopWarehouseImportTargetField.SHOP_CODE));
        assertTrue(mappings.stream().anyMatch(m -> m.getTargetField() == ShopWarehouseImportTargetField.PLATFORM_WAREHOUSE_ID));
        assertTrue(mappings.stream().anyMatch(m -> m.getTargetField() == ShopWarehouseImportTargetField.PLATFORM_WAREHOUSE_NAME));

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        assertEquals("AMZ-WH-1", result.row().platformWarehouseId());
        assertEquals("Amazon FBA 仓", result.row().platformWarehouseName());
        assertEquals(Boolean.TRUE, result.row().isDefault());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldCollectRowErrorsWhenRequiredFieldsMissing() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        Map<String, Object> rawRow = Map.of("shopName", "缺少关键字段的行");
        var result = fieldMappingService.mapRow(List.of(), rawRow, 1);

        assertTrue(result.errors().size() >= 2, "缺少平台仓库ID与本地仓库编码时应产生两条必填错误");
        assertTrue(result.errors().stream().anyMatch(e ->
                ShopWarehouseImportTargetField.PLATFORM_WAREHOUSE_ID.name().equals(e.getField())));
        assertTrue(result.errors().stream().anyMatch(e ->
                ShopWarehouseImportTargetField.WAREHOUSE_CODE.name().equals(e.getField())));
    }

    @Test
    void shouldRecordSuccessAndFailureAndCompleteJob() {
        ShopWarehouseImportJob job = ShopWarehouseImportJob.create("default-tenant", "FILE", null, "test.csv");
        job.start(3);

        assertEquals("default-tenant", job.getTenantId());
        job.recordSuccess("sw-1", false);
        job.recordSuccess("sw-2", true);
        job.recordFailure(site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseImportError.of(
                3, "S1", "P1", null, "本地仓库编码不存在"));
        job.complete();

        assertEquals(3, job.getTotalCount());
        assertEquals(2, job.getSuccessCount());
        assertEquals(1, job.getFailureCount());
        assertNotNull(job.getFinishedAt());
        assertTrue(job.hasErrors());
    }

    @Test
    void shouldBindShopWarehouseToLocalWarehouse() {
        ShopWarehouse shopWarehouse = ShopWarehouse.create("default-tenant", "AMAZON", null,
                "AMZ-STORE-01", "亚马逊店铺1", "AMZ-WH-01", "Amazon FBA 仓1", "美国加州", true);

        assertFalse(shopWarehouse.isBound());
        shopWarehouse.bindToLocalWarehouse("wh-1", "WH-001");
        assertTrue(shopWarehouse.isBound());
        assertEquals("WH-001", shopWarehouse.getLocalWarehouseCode());
    }
}
