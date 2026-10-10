package site.hexaarch.ecommerce.logistics.application.service.logistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsImportJob;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRowDto;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物流导入应用服务持久化形状测试（ISS-067 / ISS-071）：
 * 任务只在 complete() 之后单次持久化，落库时必为终态且错误明细完整；导入入口带事务注解。
 */
class LogisticsImportApplicationServiceTest {

    @Mock
    private LogisticsOrderRepository logisticsOrderRepository;
    @Mock
    private LogisticsFieldMappingRepository logisticsFieldMappingRepository;
    @Mock
    private LogisticsImportJobRepository logisticsImportJobRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private LogisticsOrderResolver logisticsOrderResolver;

    private LogisticsImportApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LogisticsImportApplicationService(
                logisticsOrderRepository,
                new LogisticsFieldMappingService(logisticsFieldMappingRepository),
                logisticsFieldMappingRepository,
                logisticsImportJobRepository,
                platformIntegrationRepository,
                new ImportSourceParser(),
                domainEventPublisher,
                logisticsOrderResolver,
                new PlatformFetcherRegistry(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
        when(logisticsImportJobRepository.save(any(LogisticsImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldPersistJobOnceInTerminalStateWithErrorDetailRows() {
        LogisticsImportRowDto invalid = new LogisticsImportRowDto();
        LogisticsImportRequest request = new LogisticsImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("AMAZON");
        request.setRows(List.of(invalid, invalid));

        LogisticsImportJobDto result = service.importLogistics(request);

        ArgumentCaptor<LogisticsImportJob> captor = ArgumentCaptor.forClass(LogisticsImportJob.class);
        verify(logisticsImportJobRepository, times(1)).save(captor.capture());
        LogisticsImportJob persisted = captor.getValue();
        assertEquals(result.getJobId(), persisted.getId());
        assertEquals(ImportJobStatus.FAILED, persisted.getStatus());
        assertEquals(2, persisted.getTotalCount());
        assertEquals(0, persisted.getSuccessCount());
        assertEquals(2, persisted.getFailureCount());
        assertEquals(2, persisted.getErrors().size());
        assertNotNull(persisted.getFinishedAt());
    }

    @Test
    void importEntryPointsShouldBeTransactional() throws NoSuchMethodException {
        assertNotNull(LogisticsImportApplicationService.class
                .getMethod("importLogistics", LogisticsImportRequest.class)
                .getAnnotation(Transactional.class), "importLogistics 必须声明 @Transactional（ISS-071）");
        assertNotNull(LogisticsImportApplicationService.class
                .getMethod("syncFromPlatform", String.class, String.class, String.class, String.class,
                        String.class, String.class, Integer.class, Boolean.class)
                .getAnnotation(Transactional.class), "syncFromPlatform 必须声明 @Transactional（自调用绕过代理，需同标）");
    }
}
