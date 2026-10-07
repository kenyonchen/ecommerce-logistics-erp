package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateProductCommand;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.ProductImportJob;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRowDto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 批量导入服务测试：验证幂等新增/更新、行级异常隔离与结果反馈统计。
 */
class ProductImportApplicationServiceTest {

    private static final String NEW_SKU = "SKU-NEW";
    private static final String EXISTING_SKU = "SKU-EXIST";
    private static final String EXISTING_PRODUCT_ID = "p-existing";

    @Mock
    private ProductApplicationService productApplicationService;
    @Mock
    private ProductFieldMappingRepository fieldMappingRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;

    private ProductImportApplicationService importService;
    private final Map<String, ProductImportJob> jobStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ProductFieldMappingService fieldMappingService = new ProductFieldMappingService(fieldMappingRepository);
        ProductImportJobRepository jobRepository = new InMemoryJobRepository();
        PlatformProductFetcher noopFetcher = new NoopFetcher();
        importService = new ProductImportApplicationService(
                productApplicationService,
                fieldMappingService,
                jobRepository,
                fieldMappingRepository,
                platformIntegrationRepository,
                new ImportSourceParser(),
                new PlatformFetcherRegistry(List.of(), List.of(noopFetcher), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                // 空的令牌交换注册表 = 只做聚合到凭据的映射，不触发外呼刷新
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
    }

    @Test
    void shouldUpsertProductsAndIsolateRowFailures() {
        Product existingProduct = Product.builder()
                .id(EXISTING_PRODUCT_ID)
                .tenantId("default-tenant")
                .name("已有商品")
                .category(ProductCategory.builder().id("c1").name("ELECTRONICS").build())
                .skus(new ArrayList<>(List.of(SKU.builder()
                        .id("s1")
                        .skuCode(EXISTING_SKU)
                        .productId(EXISTING_PRODUCT_ID)
                        .build())))
                .active(true)
                .domainEvents(new ArrayList<>())
                .build();

        // 幂等查询：按来源ID未命中；按SKU命中已有商品
        when(productApplicationService.findProductBySourceId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productApplicationService.findOptionalProductBySkuCode(EXISTING_SKU))
                .thenReturn(Optional.of(existingProduct));
        when(productApplicationService.findOptionalProductBySkuCode(NEW_SKU)).thenReturn(Optional.empty());
        when(productApplicationService.createProduct(any(CreateProductCommand.class)))
                .thenReturn(Product.builder()
                        .id("p-new")
                        .tenantId("default-tenant")
                        .name("新商品")
                        .skus(new ArrayList<>())
                        .domainEvents(new ArrayList<>())
                        .build());
        when(productApplicationService.saveImportedProduct(any())).thenReturn(existingProduct);

        ProductImportRequest request = new ProductImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("AMAZON");
        request.setUpdateExisting(true);
        request.setEnableIdempotency(true);
        request.setRows(List.of(row("新商品", NEW_SKU), row("更新商品", EXISTING_SKU), row(null, "SKU-BAD")));

        ProductImportJobDto result = importService.importProducts(request);

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(ImportJobStatus.PARTIAL_SUCCESS.name(), result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(1, result.getCreatedProductIds().size());
        assertEquals(1, result.getUpdatedProductIds().size());
        assertEquals("p-existing", result.getUpdatedProductIds().get(0));
        assertNotNull(result.getJobId());
    }

    @Test
    void shouldSkipExistingWhenUpdateDisabled() {
        Product existingProduct = Product.builder()
                .id(EXISTING_PRODUCT_ID)
                .tenantId("default-tenant")
                .name("已有商品")
                .skus(new ArrayList<>(List.of(SKU.builder()
                        .id("s1")
                        .skuCode(EXISTING_SKU)
                        .productId(EXISTING_PRODUCT_ID)
                        .build())))
                .active(true)
                .domainEvents(new ArrayList<>())
                .build();

        when(productApplicationService.findProductBySourceId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productApplicationService.findOptionalProductBySkuCode(EXISTING_SKU))
                .thenReturn(Optional.of(existingProduct));

        ProductImportRequest request = new ProductImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("FILE");
        request.setUpdateExisting(false);
        request.setRows(List.of(row("重复商品", EXISTING_SKU)));

        ProductImportJobDto result = importService.importProducts(request);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(ImportJobStatus.SUCCESS.name(), result.getStatus());
    }

    private ProductImportRowDto row(String name, String skuCode) {
        ProductImportRowDto row = new ProductImportRowDto();
        row.setName(name);
        row.setSkuCode(skuCode);
        row.setExternalProductId("EXT-" + skuCode);
        row.setActive(true);
        return row;
    }

    /** 内存版任务仓储，避免依赖数据库 */
    private class InMemoryJobRepository implements ProductImportJobRepository {
        @Override
        public ProductImportJob save(ProductImportJob job) {
            jobStore.put(job.getId(), job);
            return job;
        }

        @Override
        public Optional<ProductImportJob> findById(String jobId) {
            return Optional.ofNullable(jobStore.get(jobId));
        }

        @Override
        public PageResult<ProductImportJob> findByTenantId(String tenantId, int page, int size) {
            return new PageResult<>(new ArrayList<>(jobStore.values()), jobStore.size(), 0, size);
        }
    }

    /** 测试用空实现：测试中直接提供标准行，不触发真实平台调用 */
    private static class NoopFetcher implements PlatformProductFetcher {
        @Override
        public String type() {
            return "NOOP";
        }

        @Override
        public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
            return List.of();
        }
    }
}
