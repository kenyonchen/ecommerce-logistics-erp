package site.hexaarch.ecommerce.logistics.application.service.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.CustomerImportJob;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRowDto;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户导入应用服务持久化形状测试（ISS-067 / ISS-071）：
 * 任务只在 complete() 之后单次持久化，落库时必为终态且错误明细完整；导入入口带事务注解。
 */
class CustomerImportApplicationServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerApplicationService customerApplicationService;
    @Mock
    private CustomerFieldMappingRepository customerFieldMappingRepository;
    @Mock
    private CustomerImportJobRepository customerImportJobRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;

    private CustomerImportApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CustomerImportApplicationService(
                customerRepository,
                customerApplicationService,
                new CustomerFieldMappingService(customerFieldMappingRepository),
                customerFieldMappingRepository,
                customerImportJobRepository,
                platformIntegrationRepository,
                new ImportSourceParser(),
                new PlatformFetcherRegistry(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
        when(customerImportJobRepository.save(any(CustomerImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByExternalBuyerId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        Customer created = mock(Customer.class);
        when(created.getCustomerId()).thenReturn("CUST-1");
        when(customerApplicationService.createCustomerFromPlatform(any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(created);
    }

    @Test
    void shouldPersistJobOnceInTerminalStateWithErrorDetailRows() {
        CustomerImportRowDto valid = new CustomerImportRowDto();
        valid.setCustomerName("张三");
        valid.setEmail("zhangsan@example.com");
        valid.setExternalBuyerId("amz-buyer-001");
        CustomerImportRowDto invalid = new CustomerImportRowDto();

        CustomerImportRequest request = new CustomerImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("AMAZON");
        request.setRows(List.of(valid, invalid));

        CustomerImportJobDto result = service.importCustomers(request);

        ArgumentCaptor<CustomerImportJob> captor = ArgumentCaptor.forClass(CustomerImportJob.class);
        verify(customerImportJobRepository, times(1)).save(captor.capture());
        CustomerImportJob persisted = captor.getValue();
        assertEquals(result.getJobId(), persisted.getId());
        assertEquals(ImportJobStatus.PARTIAL_SUCCESS, persisted.getStatus());
        assertEquals(2, persisted.getTotalCount());
        assertEquals(1, persisted.getSuccessCount());
        assertEquals(1, persisted.getFailureCount());
        assertEquals(1, persisted.getErrors().size());
        assertNotNull(persisted.getFinishedAt());
    }

    @Test
    void importEntryPointsShouldBeTransactional() throws NoSuchMethodException {
        assertNotNull(CustomerImportApplicationService.class
                .getMethod("importCustomers", CustomerImportRequest.class)
                .getAnnotation(Transactional.class), "importCustomers 必须声明 @Transactional（ISS-071）");
        assertNotNull(CustomerImportApplicationService.class
                .getMethod("syncFromPlatform", String.class, String.class, String.class, String.class,
                        Integer.class, Boolean.class)
                .getAnnotation(Transactional.class), "syncFromPlatform 必须声明 @Transactional（自调用绕过代理，需同标）");
    }
}
