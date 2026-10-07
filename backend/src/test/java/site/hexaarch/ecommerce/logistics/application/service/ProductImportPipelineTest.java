package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.ProductImportJob;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductImportError;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportedProductRow;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 第三方商品导入链路核心逻辑测试：文件解析 -> 字段识别 -> 行级映射与错误收集。
 */
class ProductImportPipelineTest {

    @Mock
    private ProductFieldMappingRepository fieldMappingRepository;

    private ProductFieldMappingService fieldMappingService;
    private ImportSourceParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fieldMappingService = new ProductFieldMappingService(fieldMappingRepository);
        parser = new ImportSourceParser();
    }

    @Test
    void shouldParseCsvAndMapStandardizedFields() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String csv = "name,skuCode,categoryName,sellingPrice,costPrice,currency,weightInGrams,attributes\n"
                + "示例商品A,SKU-001,ELECTRONICS,\"1,299.00\",900.00,CNY,500,\"颜色:黑色,容量:128GB\"\n";

        List<Map<String, Object>> rawRows = parser.parse(csv);
        assertEquals(1, rawRows.size());
        assertEquals("示例商品A", rawRows.get(0).get("name"));

        List<String> sourceFields = fieldMappingService.detectSourceFields(rawRows);
        assertTrue(sourceFields.contains("skuCode"));

        var mappings = fieldMappingService.resolveMappings("default-tenant", "FILE", sourceFields);
        assertFalse(mappings.isEmpty());

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        ImportedProductRow row = result.row();

        assertEquals("示例商品A", row.name());
        assertEquals("SKU-001", row.skuCode());
        assertEquals("ELECTRONICS", row.categoryName());
        assertEquals(0, java.math.BigDecimal.valueOf(1299.00).compareTo(row.sellingPrice()));
        assertEquals(Integer.valueOf(500), row.weightInGrams());
        assertEquals(2, row.attributes().size());
        assertEquals("CNY", row.currency());
        assertTrue(result.errors().isEmpty(), "标准CSV不应产生行级错误");
    }

    @Test
    void shouldAutoDetectPlatformAliasesAndConvertUnits() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String json = """
                [
                  {"external_id":"AMZ-1","item-name":"无线耳机","seller-sku":"AMZ-SKU-1","price":99.9,
                   "item_weight":0.35,"status":"Active","variation_attributes":"颜色:白色"}
                ]
                """;
        List<Map<String, Object>> rawRows = parser.parse(json);
        var sourceFields = fieldMappingService.detectSourceFields(rawRows);
        var mappings = fieldMappingService.resolveMappings("default-tenant", "AMAZON", sourceFields);

        assertTrue(mappings.stream().anyMatch(m -> m.getTargetField() == ImportTargetField.PRODUCT_NAME));
        assertTrue(mappings.stream().anyMatch(m -> m.getTargetField() == ImportTargetField.SKU_CODE));

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        assertEquals("无线耳机", result.row().name());
        assertEquals("AMZ-SKU-1", result.row().skuCode());
        assertEquals("AMZ-1", result.row().externalProductId());
        assertEquals(1, result.row().attributes().size());
        assertNotNull(result.row().active());
        assertTrue(result.row().active());
    }

    @Test
    void shouldCollectRowLevelErrorsWithoutBreakingPipeline() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        // 缺少商品名称的一行数据
        String csv = "skuCode,sellingPrice\nSKU-002,88.00\n";
        List<Map<String, Object>> rawRows = parser.parse(csv);
        var mappings = fieldMappingService.resolveMappings("default-tenant", "FILE",
                fieldMappingService.detectSourceFields(rawRows));

        var result = fieldMappingService.mapRow(mappings, rawRows.get(0), 1);

        assertFalse(result.errors().isEmpty(), "缺失必填字段应产生行级错误");
        assertTrue(result.errors().stream().anyMatch(error ->
                ImportTargetField.PRODUCT_NAME.name().equals(error.getField())));
        assertNull(result.row().name());
        assertEquals("SKU-002", result.row().skuCode());
    }

    @Test
    void shouldAggregatePartialSuccessResult() {
        ProductImportJob job = ProductImportJob.create("default-tenant", "AMAZON", null, "products.csv");
        job.start(3);
        job.recordSuccess("p-1", false);
        job.recordSuccess("p-2", false);
        job.recordFailure(ProductImportError.of(3, null, "SKU-BAD", "PRODUCT_NAME", "商品名称为空"));
        job.complete();

        assertEquals(ImportJobStatus.PARTIAL_SUCCESS, job.getStatus());
        assertEquals(3, job.getTotalCount());
        assertEquals(2, job.getSuccessCount());
        assertEquals(1, job.getFailureCount());
        assertEquals(0, job.getSkippedCount());
        assertTrue(job.getCreatedProductIds().contains("p-1"));
        assertTrue(job.hasErrors());
        assertNotNull(job.getFinishedAt());
    }
}
