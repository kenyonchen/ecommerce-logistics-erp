package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.SamplePlatformCustomerFetcher;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRowDto;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第三方客户导入链路核心逻辑测试：数据获取(CUSTOMERS/ORDERS萃取) -> 字段映射 -> 幂等导入与结果反馈。
 *
 * @author kenyon
 */
class CustomerImportPipelineTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerApplicationService customerApplicationService;
    @Mock
    private CustomerFieldMappingRepository fieldMappingRepository;
    @Mock
    private CustomerImportJobRepository importJobRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    @Mock
    private PlatformCustomerFetcher httpCustomerFetcher;
    @Mock
    private PlatformCustomerFetcher sampleCustomerFetcher;
    @Mock
    private PlatformOrderFetcher httpOrderFetcher;
    @Mock
    private PlatformOrderFetcher sampleOrderFetcher;

    private CustomerImportApplicationService applicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 注册表在构造时按 type() 建索引，所以注册键要在构造应用服务之前打桩
        when(httpCustomerFetcher.type()).thenReturn(PlatformAuthType.CUSTOM_SHOP.name());
        when(sampleCustomerFetcher.type()).thenReturn(PlatformFetcherRegistry.SAMPLE_TYPE);
        when(httpOrderFetcher.type()).thenReturn(PlatformAuthType.CUSTOM_SHOP.name());
        when(sampleOrderFetcher.type()).thenReturn(PlatformFetcherRegistry.SAMPLE_TYPE);
        PlatformFetcherRegistry fetcherRegistry = new PlatformFetcherRegistry(
                List.of(sampleOrderFetcher, httpOrderFetcher),
                List.of(),
                List.of(sampleCustomerFetcher, httpCustomerFetcher),
                List.of(), List.of(), List.of(), List.of(), List.of());
        CustomerFieldMappingService fieldMappingService = new CustomerFieldMappingService(fieldMappingRepository);
        applicationService = new CustomerImportApplicationService(customerRepository, customerApplicationService,
                fieldMappingService, fieldMappingRepository, importJobRepository, platformIntegrationRepository,
                new ImportSourceParser(), fetcherRegistry,
                // 空的令牌交换注册表 = 只做聚合到凭据的映射，不触发外呼刷新
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());
        when(customerRepository.findByExternalBuyerId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldImportCustomersFromPlatformSourceWithRowLevelFaultTolerance() {
        // 两行平台买家数据：第一行完整，第二行缺邮箱（走 externalBuyerId 新建路径）
        List<Map<String, Object>> rawRows = List.of(
                linkedRow("buyerId", "b-1", "buyerName", "买家一号", "buyerEmail", "b1@example.com",
                        "country", "中国", "city", "深圳市"),
                linkedRow("buyerId", "b-2", "buyerName", "买家二号"));
        when(sampleCustomerFetcher.fetch(any(PlatformCustomerFetchRequest.class), nullable(PlatformCredential.class)))
                .thenReturn(rawRows);

        CustomerImportPreviewRequest previewRequest = new CustomerImportPreviewRequest();
        previewRequest.setPlatform("EBAY");
        previewRequest.setUseSampleData(Boolean.TRUE);
        CustomerImportPreviewResponse previewed = applicationService.preview(previewRequest);

        assertEquals(2, previewed.getTotalCount());
        assertEquals("b-1", previewed.getRows().get(0).getExternalBuyerId());
        assertEquals("买家一号", previewed.getRows().get(0).getCustomerName());
        assertEquals("中国 深圳市", previewed.getRows().get(0).getFullAddress());
        assertTrue(previewed.getRowErrors().isEmpty(), "标准买家行不应产生行级错误");

        when(customerApplicationService.createCustomerFromPlatform(anyString(), anyString(), any(), any(), any(),
                any(), anyString(), any())).thenAnswer(invocation -> restoredCustomer(
                "b-1".equals(invocation.getArgument(7)) ? "CUST-1" : "CUST-2",
                invocation.getArgument(1), invocation.getArgument(2)));

        CustomerImportRequest importRequest = new CustomerImportRequest();
        importRequest.setPlatform(previewed.getPlatform());
        importRequest.setRows(previewed.getRows());
        CustomerImportJobDto job = applicationService.importCustomers(importRequest);

        assertEquals(ImportJobStatus.SUCCESS.name(), job.getStatus());
        assertEquals(2, job.getSuccessCount());
        assertEquals(0, job.getFailureCount());
        assertEquals(List.of("CUST-1", "CUST-2"), job.getCreatedCustomerIds());
        // 完整行走「买家ID未命中 -> 邮箱兜底」的幂等查找；缺邮箱的行只按平台买家ID查找
        verify(customerRepository).findByEmail("b1@example.com");
        verify(customerRepository).findByExternalBuyerId("default-tenant", "EBAY", "b-2");
    }

    @Test
    void shouldDeduplicateBuyersWhenSourcingFromOrders() {
        // 三条订单行、两个买家：amz-buyer-001 出现两次，萃取后应合并为一个买家
        List<Map<String, Object>> orderRows = List.of(
                linkedRow("amazon-order-id", "AMZ-ORD-1", "buyer_id", "amz-buyer-001", "buyer-name", "张三"),
                linkedRow("amazon-order-id", "AMZ-ORD-2", "buyer_id", "amz-buyer-001", "buyer-name", "张三"),
                linkedRow("amazon-order-id", "AMZ-ORD-3", "buyer_id", "amz-buyer-002", "buyer-name", "李四"));
        when(sampleOrderFetcher.fetch(any(PlatformOrderFetchRequest.class), nullable(PlatformCredential.class)))
                .thenReturn(orderRows);

        CustomerImportPreviewRequest request = new CustomerImportPreviewRequest();
        request.setPlatform("AMAZON");
        request.setSourceType(CustomerImportApplicationService.SOURCE_TYPE_ORDERS);
        request.setUseSampleData(Boolean.TRUE);
        CustomerImportPreviewResponse previewed = applicationService.preview(request);

        assertEquals(2, previewed.getTotalCount());
        assertEquals("amz-buyer-001", previewed.getRows().get(0).getExternalBuyerId());
        assertEquals("张三", previewed.getRows().get(0).getCustomerName());
        assertTrue(previewed.getWarnings().stream().anyMatch(warning ->
                warning.contains("已从订单数据中按买家去重") && warning.contains("3 条订单行合并为 2 个买家")));
    }

    @Test
    void shouldMergeExistingCustomerByEmailAsUpdate() {
        Customer existing = restoredCustomer("CUST-EXIST", "老客户", "old@example.com");
        when(customerRepository.findByEmail("old@example.com")).thenReturn(Optional.of(existing));

        CustomerImportRequest request = new CustomerImportRequest();
        request.setPlatform("SHOPIFY");
        request.setRows(new ArrayList<>(List.of(row(1, null, "老客户", "old@example.com", "13800138000"))));
        CustomerImportJobDto job = applicationService.importCustomers(request);

        assertEquals(1, job.getSuccessCount());
        assertEquals(List.of("CUST-EXIST"), job.getUpdatedCustomerIds());
        assertTrue(job.getCreatedCustomerIds().isEmpty());
        // 合并更新：补齐了缺失的电话，不新建客户
        assertEquals("13800138000", existing.getPhone());
        verify(customerApplicationService, never()).createCustomerFromPlatform(any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void shouldSkipExistingCustomerWhenUpdateExistingDisabled() {
        Customer existing = restoredCustomer("CUST-EXIST", "老客户", "old@example.com");
        when(customerRepository.findByEmail("old@example.com")).thenReturn(Optional.of(existing));

        CustomerImportRequest request = new CustomerImportRequest();
        request.setPlatform("SHOPIFY");
        request.setUpdateExisting(Boolean.FALSE);
        request.setRows(new ArrayList<>(List.of(row(1, null, "老客户", "old@example.com", "13800138000"))));
        CustomerImportJobDto job = applicationService.importCustomers(request);

        assertEquals(0, job.getSuccessCount());
        assertEquals(1, job.getSkippedCount());
        assertEquals(ImportJobStatus.SUCCESS.name(), job.getStatus());
        assertFalse(job.getErrors().isEmpty(), "跳过的行应保留错误明细供回溯");
        assertTrue(job.getErrors().get(0).getMessage().contains("未开启覆盖更新"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void shouldBuildSampleRowsPerPlatformWithExpectedShape() {
        SamplePlatformCustomerFetcher fetcher = new SamplePlatformCustomerFetcher();

        List<Map<String, Object>> amazonRows = fetcher.fetch(
                new PlatformCustomerFetchRequest("t", "AMAZON", null, null, null, null, 5, 1));
        assertEquals(5, amazonRows.size());
        // 亚马逊：仅买家ID/姓名/邮箱，无电话与地址
        assertTrue(amazonRows.get(0).containsKey("buyer_id"));
        assertTrue(amazonRows.get(0).containsKey("buyer-name"));
        assertFalse(amazonRows.get(0).containsKey("phone"));
        // index==3 的行故意缺失邮箱，验证行级容错
        assertFalse(amazonRows.get(2).containsKey("buyer-email"));
        assertTrue(amazonRows.get(1).containsKey("buyer-email"));

        List<Map<String, Object>> shopifyRows = fetcher.fetch(
                new PlatformCustomerFetchRequest("t", "SHOPIFY", null, null, null, null, 2, 1));
        assertTrue(shopifyRows.get(0).containsKey("customerName"));
        // Shopify 不提供买家外部ID：演示邮箱兜底去重路径
        assertFalse(shopifyRows.get(0).containsKey("buyerId"));
        assertTrue(shopifyRows.get(0).containsKey("phoneNumber"));

        List<Map<String, Object>> ebayRows = fetcher.fetch(
                new PlatformCustomerFetchRequest("t", "EBAY", null, null, null, null, 4, 1));
        assertTrue(ebayRows.get(0).containsKey("buyerId"));
        assertTrue(ebayRows.get(0).containsKey("streetAddress"));
        // 公司字段只给部分行
        assertTrue(ebayRows.get(1).containsKey("company"));
        assertFalse(ebayRows.get(0).containsKey("company"));

        List<Map<String, Object>> defaultRows = fetcher.fetch(
                new PlatformCustomerFetchRequest("t", "OTHER", null, null, null, null, null, 1));
        // limit 缺省为5行
        assertEquals(5, defaultRows.size());
        assertTrue(defaultRows.get(0).containsKey("customerName"));
        assertTrue(defaultRows.get(0).containsKey("customerEmail"));
        assertTrue(defaultRows.get(0).containsKey("address"));
    }

    private CustomerImportRowDto row(int rowNumber, String externalBuyerId, String customerName,
                                     String email, String phone) {
        CustomerImportRowDto dto = new CustomerImportRowDto();
        dto.setRowNumber(rowNumber);
        dto.setExternalBuyerId(externalBuyerId);
        dto.setCustomerName(customerName);
        dto.setEmail(email);
        dto.setPhone(phone);
        return dto;
    }

    private Customer restoredCustomer(String customerId, String customerName, String email) {
        LocalDateTime now = LocalDateTime.now();
        return Customer.restore("default-tenant", customerId, customerName, email, null, null,
                null, null, null, CustomerStatus.ACTIVE, now, now);
    }

    private Map<String, Object> linkedRow(Object... keyValuePairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            row.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return row;
    }
}
