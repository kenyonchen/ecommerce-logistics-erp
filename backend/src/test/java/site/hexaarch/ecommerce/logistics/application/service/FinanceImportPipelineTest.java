package site.hexaarch.ecommerce.logistics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinanceImportJob;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceImportError;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinanceFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.FinanceImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.ImportedFinanceRow;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.platform.client.FinanceFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFinanceFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRowDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 平台财务数据导入链路核心逻辑测试（与订单/物流导入测试同构）：
 * 文件解析 -> 字段识别 -> 行级映射与错误收集 -> 幂等去重（跳过/覆盖更新） -> 任务计数与错误明细。
 *
 * @author kenyon
 */
class FinanceImportPipelineTest {

    private static final String TENANT = "default-tenant";
    private static final String EXISTING_TXN_ID = "TXN-EXIST-001";

    @Mock
    private FinanceFieldMappingRepository fieldMappingRepository;
    @Mock
    private FinancialTransactionService financialTransactionService;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private FinanceFieldMappingService fieldMappingService;
    private FinanceImportApplicationService importService;
    private ImportSourceParser parser;
    private final Map<String, FinanceImportJob> jobStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fieldMappingService = new FinanceFieldMappingService(fieldMappingRepository);
        parser = new ImportSourceParser();
        importService = new FinanceImportApplicationService(
                financialTransactionService,
                financialTransactionRepository,
                fieldMappingService,
                fieldMappingRepository,
                new InMemoryJobRepository(),
                platformIntegrationRepository,
                parser,
                domainEventPublisher,
                new PlatformFetcherRegistry(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new NoopFetcher()), List.of(), List.of()),
                // 空的令牌交换注册表 = 只做聚合到凭据的映射，不触发外呼刷新
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
    }

    // ---------- 字段映射阶段 ----------

    @Test
    void shouldParseCsvAndMapStandardFinanceFields() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        String csv = "externalTransactionId,type,amount,currency,referenceId,referenceType,description,status,occurredAt\n"
                + "AMAZON-FIN-001,payout,\"¥1,299.00\",cny,AMAZON-ORD-001,ORDER,示例平台回款,settled,2026-09-01 10:00:00\n";

        List<Map<String, Object>> rawRows = parser.parse(csv);
        assertEquals(1, rawRows.size());

        List<String> sourceFields = fieldMappingService.detectSourceFields(rawRows);
        assertTrue(sourceFields.contains("externalTransactionId"));

        List<FinanceFieldMapping> mappings = fieldMappingService.resolveMappings(TENANT, "FILE", sourceFields);
        assertFalse(mappings.isEmpty());

        FinanceFieldMappingService.MappingResult result =
                fieldMappingService.mapRow(mappings, rawRows.get(0), 1);
        ImportedFinanceRow row = result.row();

        assertEquals("AMAZON-FIN-001", row.externalTransactionId());
        // 平台词转换：payout -> INCOME；货币符号与千分位清洗；ISO码归一大写；settled -> SUCCESS
        assertEquals(TransactionType.INCOME, row.type());
        assertEquals(0, new BigDecimal("1299.00").compareTo(row.amount()));
        assertEquals("CNY", row.currency());
        assertEquals("AMAZON-ORD-001", row.referenceId());
        assertEquals("ORDER", row.referenceType());
        assertEquals("示例平台回款", row.description());
        assertEquals(TransactionStatus.SUCCESS, row.status());
        assertNotNull(row.occurredAt());
        assertTrue(result.errors().isEmpty(), "标准CSV不应产生行级错误");
    }

    @Test
    void shouldAutoDetectPlatformKebabCaseSettlementFields() {
        when(fieldMappingRepository.findByTenantIdAndPlatform(anyString(), anyString())).thenReturn(List.of());

        Map<String, Object> rawRow = new LinkedHashMap<>();
        rawRow.put("settlement-id", "EBAY-FIN-002");
        rawRow.put("transaction-type", "commission");
        rawRow.put("gross-amount", -18.50);
        rawRow.put("currency-code", "usd");
        rawRow.put("memo", "平台佣金扣收");

        var sourceFields = fieldMappingService.detectSourceFields(List.of(rawRow));
        var mappings = fieldMappingService.resolveMappings(TENANT, "EBAY", sourceFields);

        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == FinanceImportTargetField.EXTERNAL_TRANSACTION_ID));
        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == FinanceImportTargetField.TYPE));
        assertTrue(mappings.stream()
                .anyMatch(m -> m.getTargetField() == FinanceImportTargetField.AMOUNT));

        var result = fieldMappingService.mapRow(mappings, rawRow, 1);
        assertEquals("EBAY-FIN-002", result.row().externalTransactionId());
        assertEquals(TransactionType.EXPENSE, result.row().type(), "commission 应识别为支出");
        assertEquals(0, new BigDecimal("-18.5").compareTo(result.row().amount()));
        assertEquals("USD", result.row().currency());
        assertEquals("平台佣金扣收", result.row().description());
        // 缺省状态：平台未给出 status 时默认 SUCCESS
        assertEquals(TransactionStatus.SUCCESS, result.row().status());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldCollectRequiredFieldErrorsWhenMissing() {
        Map<String, Object> rawRow = new LinkedHashMap<>();
        rawRow.put("description", "只有描述，核心字段全缺");

        var mappings = fieldMappingService.autoDetect(fieldMappingService.detectSourceFields(List.of(rawRow)));
        var result = fieldMappingService.mapRow(mappings, rawRow, 2);

        List<String> errorFields = result.errors().stream().map(FinanceImportError::getField).toList();
        assertTrue(errorFields.contains(FinanceImportTargetField.EXTERNAL_TRANSACTION_ID.name()));
        assertTrue(errorFields.contains(FinanceImportTargetField.TYPE.name()));
        assertTrue(errorFields.contains(FinanceImportTargetField.AMOUNT.name()));
        assertTrue(errorFields.contains(FinanceImportTargetField.CURRENCY.name()));
    }

    @Test
    void shouldReportErrorsForIllegalAmountAndCurrency() {
        Map<String, Object> rawRow = new LinkedHashMap<>();
        rawRow.put("externalTransactionId", "SHOPIFY-FIN-003");
        rawRow.put("type", "refund");
        rawRow.put("amount", "not-a-number");
        rawRow.put("currency", "RMB¥");

        var mappings = fieldMappingService.autoDetect(fieldMappingService.detectSourceFields(List.of(rawRow)));
        var result = fieldMappingService.mapRow(mappings, rawRow, 3);

        assertEquals(TransactionType.REFUND, result.row().type());
        assertNull(result.row().amount(), "非法金额应解析失败且行内不抛异常");
        assertTrue(result.errors().stream().anyMatch(error ->
                FinanceImportTargetField.AMOUNT.name().equals(error.getField())
                        && error.getMessage().contains("无法转换为数值")));
        assertTrue(result.errors().stream().anyMatch(error ->
                FinanceImportTargetField.CURRENCY.name().equals(error.getField())
                        && error.getMessage().contains("ISO三字母码")));
    }

    @Test
    void shouldConvertTypeStatusAndDateTimeDictionaries() {
        assertEquals(TransactionType.INCOME,
                fieldMappingService.toTransactionType("payment_received", null, 0, null));
        assertEquals(TransactionType.ADJUSTMENT,
                fieldMappingService.toTransactionType("reconciliation", null, 0, null));
        assertEquals(TransactionType.INCOME,
                fieldMappingService.toTransactionType("INCOME", null, 0, null), "应兼容系统枚举名直传");

        assertEquals(TransactionStatus.PENDING, fieldMappingService.toTransactionStatus("processing"));
        assertEquals(TransactionStatus.FAILED, fieldMappingService.toTransactionStatus("declined"));
        assertEquals(TransactionStatus.CANCELLED, fieldMappingService.toTransactionStatus("voided"));
        assertEquals(TransactionStatus.SUCCESS, fieldMappingService.toTransactionStatus("无法识别的状态"),
                "无法识别状态应回退为 SUCCESS");

        assertNotNull(fieldMappingService.toDateTime("2026-09-20 10:00:00"));
        assertNotNull(fieldMappingService.toDateTime("20260920100000"));
        assertNotNull(fieldMappingService.toDateTime("2026-09-20"));
        assertNotNull(fieldMappingService.toDateTime("2026-01-25T10:20:30Z"));
    }

    // ---------- 任务聚合阶段 ----------

    @Test
    void shouldRecordSuccessSkipFailureAndCompleteJob() {
        FinanceImportJob job = FinanceImportJob.create(TENANT, "AMAZON", null, "finance.csv");
        job.start(4);
        job.recordSuccess("tx-1", false);
        job.recordSuccess(EXISTING_TXN_ID, true);
        job.recordSkipped(FinanceImportError.of(3, "AMAZON-FIN-003",
                FinanceImportTargetField.EXTERNAL_TRANSACTION_ID.name(), "平台交易流水号已存在且未开启覆盖更新，已跳过"));
        job.recordFailure(FinanceImportError.of(4, "AMAZON-FIN-004", null, "金额为空，无法导入"));
        job.complete();

        assertEquals(4, job.getTotalCount());
        assertEquals(2, job.getSuccessCount());
        assertEquals(1, job.getSkippedCount());
        assertEquals(1, job.getFailureCount());
        assertNotNull(job.getFinishedAt());
        assertTrue(job.hasErrors());
        assertTrue(job.getCreatedTransactionIds().contains("tx-1"));
        assertTrue(job.getUpdatedTransactionIds().contains(EXISTING_TXN_ID));
    }

    // ---------- 应用服务幂等阶段 ----------

    @Test
    void shouldUpdateExistingAndKeepTransactionIdWhenUpdateEnabled() {
        FinancialTransaction existing = existingTransaction();
        when(financialTransactionRepository.findByReference("AMAZON:AMAZON-FIN-001", "PLATFORM_TRANSACTION"))
                .thenReturn(List.of(existing));

        FinanceImportRequest request = request(true, row("AMAZON-FIN-001", "INCOME", "199.00"));

        FinanceImportJobDto result = importService.importTransactions(request);

        assertEquals(ImportJobStatus.SUCCESS.name(), result.getStatus());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(List.of(EXISTING_TXN_ID), result.getUpdatedTransactionIds(), "覆盖更新须保持 transactionId 不变");
        assertEquals(0, new BigDecimal("199.00").compareTo(existing.getAmount()));
        assertEquals(TransactionStatus.SUCCESS, existing.getStatus());
    }

    @Test
    void shouldSkipExistingWhenUpdateDisabled() {
        when(financialTransactionRepository.findByReference("AMAZON:AMAZON-FIN-001", "PLATFORM_TRANSACTION"))
                .thenReturn(List.of(existingTransaction()));

        FinanceImportRequest request = request(false, row("AMAZON-FIN-001", "INCOME", "199.00"));

        FinanceImportJobDto result = importService.importTransactions(request);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(ImportJobStatus.SUCCESS.name(), result.getStatus());
        assertEquals(1, result.getErrors().size(), "跳过应记录错误明细");
    }

    @Test
    void shouldCreateNewRowAndIsolateRowFailureWithoutBreakingBatch() {
        when(financialTransactionService.createFinancialTransaction(
                eq(TransactionType.EXPENSE), any(BigDecimal.class), eq("USD"),
                eq("AMAZON:AMAZON-FIN-002"), eq("PLATFORM_TRANSACTION"), any(), eq(TENANT)))
                .thenReturn(FinancialTransaction.create("TXN-NEW-002", TENANT, TransactionType.EXPENSE,
                        new BigDecimal("-18.50"), "USD", "AMAZON:AMAZON-FIN-002", "PLATFORM_TRANSACTION", "佣金"));

        // 第二行缺失金额 -> 行级失败，不影响第一行入库
        FinanceImportRowDto bad = row("AMAZON-FIN-003", "INCOME", null);
        FinanceImportJobDto result = importService.importTransactions(
                request(true, row("AMAZON-FIN-002", "EXPENSE", "-18.50"), bad));

        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(ImportJobStatus.PARTIAL_SUCCESS.name(), result.getStatus());
        assertEquals(List.of("TXN-NEW-002"), result.getCreatedTransactionIds());
        assertFalse(result.getErrors().isEmpty());
        assertEquals("AMAZON-FIN-003", result.getErrors().get(0).getExternalTransactionId());
        assertNotNull(result.getJobId());
        assertTrue(jobStore.containsKey(result.getJobId()), "任务应落库供 job 查询接口读取");
    }

    // ---------- 辅助构造 ----------

    private FinanceImportRequest request(boolean updateExisting, FinanceImportRowDto... rows) {
        FinanceImportRequest request = new FinanceImportRequest();
        request.setTenantId(TENANT);
        request.setPlatform("AMAZON");
        request.setFileName("finance.csv");
        request.setUpdateExisting(updateExisting);
        request.setEnableIdempotency(true);
        request.setRows(List.of(rows));
        return request;
    }

    private FinanceImportRowDto row(String externalTransactionId, String type, String amount) {
        FinanceImportRowDto row = new FinanceImportRowDto();
        row.setExternalTransactionId(externalTransactionId);
        row.setType(type);
        row.setAmount(amount == null ? null : new BigDecimal(amount));
        row.setCurrency("USD");
        row.setDescription("导入行-" + externalTransactionId);
        row.setStatus("SUCCESS");
        return row;
    }

    private FinancialTransaction existingTransaction() {
        return FinancialTransaction.builder()
                .transactionId(EXISTING_TXN_ID)
                .tenantId(TENANT)
                .type(TransactionType.INCOME)
                .amount(new BigDecimal("100.00"))
                .currency("CNY")
                .referenceId("AMAZON:AMAZON-FIN-001")
                .referenceType("PLATFORM_TRANSACTION")
                .description("历史导入")
                .status(TransactionStatus.PENDING)
                .build();
    }

    /** 内存版任务仓储，避免依赖数据库 */
    private class InMemoryJobRepository implements FinanceImportJobRepository {
        @Override
        public FinanceImportJob save(FinanceImportJob job) {
            jobStore.put(job.getId(), job);
            return job;
        }

        @Override
        public Optional<FinanceImportJob> findById(String jobId) {
            return Optional.ofNullable(jobStore.get(jobId));
        }

        @Override
        public PageResult<FinanceImportJob> findByTenantId(String tenantId, int page, int size) {
            return new PageResult<>(new ArrayList<>(jobStore.values()), jobStore.size(), 0, size);
        }
    }

    /** 测试用空实现：测试中直接提供标准行，不触发真实平台调用 */
    private static class NoopFetcher implements PlatformFinanceFetcher {
        @Override
        public String type() {
            return "NOOP";
        }

        @Override
        public List<Map<String, Object>> fetch(FinanceFetchRequest request) {
            return List.of();
        }
    }
}
