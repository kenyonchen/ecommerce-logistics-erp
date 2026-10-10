package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinanceImportJob;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinanceFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRowDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财务导入应用服务持久化形状测试（ISS-067 / ISS-071）：
 * 任务只在 complete() 之后单次持久化，落库时必为终态且错误明细完整；导入入口带事务注解。
 */
class FinanceImportApplicationServiceTest {

    @Mock
    private FinancialTransactionService financialTransactionService;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private FinanceFieldMappingRepository financeFieldMappingRepository;
    @Mock
    private FinanceImportJobRepository financeImportJobRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private FinanceImportApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FinanceImportApplicationService(
                financialTransactionService,
                financialTransactionRepository,
                new FinanceFieldMappingService(financeFieldMappingRepository),
                financeFieldMappingRepository,
                financeImportJobRepository,
                platformIntegrationRepository,
                new ImportSourceParser(),
                domainEventPublisher,
                new PlatformFetcherRegistry(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
        when(financeImportJobRepository.save(any(FinanceImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldPersistJobOnceInTerminalStateWithErrorDetailRows() {
        FinanceImportRowDto missingId = new FinanceImportRowDto();
        FinanceImportRowDto missingAmount = new FinanceImportRowDto();
        missingAmount.setExternalTransactionId("PAYOUT-1");

        FinanceImportRequest request = new FinanceImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("AMAZON");
        request.setRows(List.of(missingId, missingAmount));

        FinanceImportJobDto result = service.importTransactions(request);

        ArgumentCaptor<FinanceImportJob> captor = ArgumentCaptor.forClass(FinanceImportJob.class);
        verify(financeImportJobRepository, times(1)).save(captor.capture());
        FinanceImportJob persisted = captor.getValue();
        assertEquals(result.getJobId(), persisted.getId());
        assertEquals(ImportJobStatus.FAILED, persisted.getStatus());
        assertEquals(2, persisted.getTotalCount());
        assertEquals(2, persisted.getFailureCount());
        assertEquals(2, persisted.getErrors().size());
        assertNotNull(persisted.getFinishedAt());
    }

    @Test
    void importEntryPointsShouldBeTransactional() throws NoSuchMethodException {
        assertNotNull(FinanceImportApplicationService.class
                .getMethod("importTransactions", FinanceImportRequest.class)
                .getAnnotation(Transactional.class), "importTransactions 必须声明 @Transactional（ISS-071）");
        assertNotNull(FinanceImportApplicationService.class
                .getMethod("syncFromPlatform", String.class, String.class, String.class, String.class,
                        String.class, Integer.class, Boolean.class)
                .getAnnotation(Transactional.class), "syncFromPlatform 必须声明 @Transactional（自调用绕过代理，需同标）");
    }
}
