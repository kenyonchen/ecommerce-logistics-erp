package site.hexaarch.ecommerce.logistics.application.service.finance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
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
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.FinanceFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFinanceFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRowDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方电商平台财务数据导入应用服务。
 * <p>
 * 完整链路：财务数据获取 -> 字段映射 -> 批量导入 -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * 落库幂等约定：referenceType 固定 {@code PLATFORM_TRANSACTION}，referenceId = platform + ":" + externalTransactionId。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class FinanceImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    public static final String DEFAULT_TENANT_ID = "default-tenant";
    /**
     * 平台导入流水的关联类型（幂等键的一部分，前端契约依赖，不得变更）
     */
    public static final String PLATFORM_REFERENCE_TYPE = "PLATFORM_TRANSACTION";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FinancialTransactionService financialTransactionService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinanceFieldMappingService financeFieldMappingService;
    private final FinanceFieldMappingRepository financeFieldMappingRepository;
    private final FinanceImportJobRepository financeImportJobRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final DomainEventPublisher domainEventPublisher;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public FinanceImportApplicationService(FinancialTransactionService financialTransactionService,
                                           FinancialTransactionRepository financialTransactionRepository,
                                           FinanceFieldMappingService financeFieldMappingService,
                                           FinanceFieldMappingRepository financeFieldMappingRepository,
                                           FinanceImportJobRepository financeImportJobRepository,
                                           PlatformIntegrationRepository platformIntegrationRepository,
                                           ImportSourceParser importSourceParser,
                                           DomainEventPublisher domainEventPublisher,
                                           PlatformFetcherRegistry fetcherRegistry,
                                           PlatformCredentials platformCredentials) {
        this.financialTransactionService = financialTransactionService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.financeFieldMappingService = financeFieldMappingService;
        this.financeFieldMappingRepository = financeFieldMappingRepository;
        this.financeImportJobRepository = financeImportJobRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.domainEventPublisher = domainEventPublisher;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始数据并完成字段映射，不写入财务流水库
     */
    public FinanceImportPreviewResponse preview(FinanceImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = financeFieldMappingService.detectSourceFields(rawRows);

        List<FinanceFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : financeFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<FinanceImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<FinanceImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = financeFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            FinanceImportRowDto dto = toRowDto(result.row());
            dto.setErrors(toErrorDtos(result.errors()));
            rows.add(dto);
            rowErrors.addAll(toErrorDtos(result.errors()));
        }

        if (mappings.isEmpty()) {
            warnings.add("未能识别出任何字段映射，请在字段映射中手工指定平台字段与系统字段的对应关系");
        }
        if (!rowErrors.isEmpty()) {
            warnings.add("存在 " + rowErrors.size() + " 条字段级错误，导入时将跳过对应行");
        }

        FinanceImportPreviewResponse response = new FinanceImportPreviewResponse();
        response.setTenantId(tenantId);
        response.setPlatform(platform);
        response.setIntegrationId(integration == null ? null : String.valueOf(integration.getId()));
        response.setFileName(request.getFileName());
        response.setSourceFields(sourceFields);
        response.setMappings(toMappingDtos(mappings));
        response.setRows(rows);
        response.setTotalCount(rows.size());
        response.setRowErrors(rowErrors);
        response.setWarnings(warnings);
        return response;
    }

    /**
     * 批量导入：逐行处理，行级异常不影响其他行，最终产出导入任务与结果反馈
     */
    public FinanceImportJobDto importTransactions(FinanceImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();
        boolean idempotency = request.getEnableIdempotency() == null || request.getEnableIdempotency();

        List<FinanceImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            FinanceImportPreviewRequest previewRequest = new FinanceImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setExternalTransactionId(request.getExternalTransactionId());
            previewRequest.setUpdatedFrom(request.getUpdatedFrom());
            previewRequest.setUpdatedTo(request.getUpdatedTo());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setPage(request.getPage());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            FinanceImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        FinanceImportJob job = FinanceImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());
        persistJob(job);

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                FinanceImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, updateExisting, idempotency, job);
                } catch (Exception e) {
                    log.warn("[FinanceImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(FinanceImportError.of(rowNumber, row.getExternalTransactionId(),
                            null, normalizeErrorMessage(e)));
                }
            }
        }
        job.complete();
        persistJob(job);
        return toJobDto(job, true);
    }

    /**
     * 从指定平台集成同步财务数据（供「平台集成-同步财务」入口调用）
     */
    public FinanceImportJobDto syncFromPlatform(String integrationId, String tenantId,
                                                String externalTransactionId, String updatedFrom,
                                                String updatedTo, Integer limit, Boolean useSampleData) {
        FinanceImportRequest request = new FinanceImportRequest();
        request.setIntegrationId(integrationId);
        request.setTenantId(tenantId);
        request.setExternalTransactionId(externalTransactionId);
        request.setUpdatedFrom(updatedFrom);
        request.setUpdatedTo(updatedTo);
        request.setLimit(limit);
        request.setUseSampleData(useSampleData);
        request.setUpdateExisting(Boolean.TRUE);
        request.setEnableIdempotency(Boolean.TRUE);
        return importTransactions(request);
    }

    /**
     * 查询导入任务详情
     */
    public FinanceImportJobDto getJob(String jobId) {
        return financeImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<FinanceImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<FinanceImportJob> jobs = financeImportJobRepository.findByTenantId(tenantId, page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<FieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(financeFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<FieldMappingDto> saveMappings(FieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : FILE_PLATFORM;
        List<FinanceFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (FinanceFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        financeFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<FinanceFieldMapping> saved = financeFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (FinanceImportTargetField field : FinanceImportTargetField.values()) {
            fields.add(new TargetFieldDto(field.name(), field.getLabel(), field.isRequired()));
        }
        return fields;
    }

    /**
     * 字段转换规则列表
     */
    public List<TargetFieldDto> transforms() {
        List<TargetFieldDto> transforms = new ArrayList<>();
        for (FieldTransform transform : FieldTransform.values()) {
            transforms.add(new TargetFieldDto(transform.name(), transform.getLabel(), Boolean.FALSE));
        }
        return transforms;
    }

    /**
     * 交易类型字典，供导入时类型映射与前端下拉使用
     */
    public List<TargetFieldDto> transactionTypes() {
        List<TargetFieldDto> types = new ArrayList<>();
        for (TransactionType type : TransactionType.values()) {
            types.add(new TargetFieldDto(type.name(), type.getDescription(), Boolean.FALSE));
        }
        return types;
    }

    /**
     * 生成标准CSV导入模板
     */
    public String csvTemplate() {
        return String.join(",", List.of("externalTransactionId", "type", "amount", "currency", "referenceId",
                "referenceType", "description", "status", "occurredAt")) + "\n"
                + "AMAZON-FIN-001,payout,199.00,CNY,AMAZON-ORD-001,ORDER,示例平台回款,SUCCESS,"
                + "2026-09-01 10:00:00\n";
    }

    private void processRow(FinanceImportRowDto row, int rowNumber, String tenantId, String platform,
                            boolean updateExisting, boolean idempotency, FinanceImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            FinanceImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        String externalTransactionId = row.getExternalTransactionId();
        if (!StringUtils.hasText(externalTransactionId)) {
            throw new ValidationException("平台交易流水号为空，无法建立幂等关联");
        }
        TransactionType type = parseTransactionType(row.getType());
        if (row.getAmount() == null) {
            throw new ValidationException("金额为空，无法导入");
        }
        if (!StringUtils.hasText(row.getCurrency())) {
            throw new ValidationException("币种为空，无法导入");
        }
        TransactionStatus status = parseTransactionStatus(row.getStatus());
        // 幂等键约定（前端契约依赖）：referenceType 固定 PLATFORM_TRANSACTION，referenceId = platform:externalTransactionId
        String referenceId = platform + ":" + externalTransactionId;

        FinancialTransaction existing = idempotency
                ? financialTransactionRepository.findByReference(referenceId, PLATFORM_REFERENCE_TYPE)
                        .stream().findFirst().orElse(null)
                : null;

        if (existing != null) {
            if (!updateExisting) {
                job.recordSkipped(FinanceImportError.of(rowNumber, externalTransactionId,
                        FinanceImportTargetField.EXTERNAL_TRANSACTION_ID.name(),
                        "平台交易流水号已存在且未开启覆盖更新，已跳过"));
                return;
            }
            // 更新金额/状态/描述，transactionId 保持不变
            existing.applyPlatformUpdate(row.getAmount(), status, row.getDescription());
            financialTransactionRepository.save(existing);
            domainEventPublisher.publishEventsFrom(existing);
            job.recordSuccess(existing.getTransactionId(), true);
            return;
        }

        FinancialTransaction created = financialTransactionService.createFinancialTransaction(
                type,
                row.getAmount(),
                row.getCurrency(),
                referenceId,
                PLATFORM_REFERENCE_TYPE,
                row.getDescription(),
                tenantId);
        if (status != null && status != TransactionStatus.PENDING) {
            created.applyPlatformUpdate(null, status, null);
            financialTransactionRepository.save(created);
        }
        domainEventPublisher.publishEventsFrom(created);
        job.recordSuccess(created.getTransactionId(), false);
    }

    private TransactionType parseTransactionType(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return TransactionType.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("不支持的交易类型: " + text);
        }
    }

    private TransactionStatus parseTransactionStatus(String text) {
        if (!StringUtils.hasText(text)) {
            return TransactionStatus.SUCCESS;
        }
        try {
            return TransactionStatus.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TransactionStatus.SUCCESS;
        }
    }

    private List<Map<String, Object>> resolveRawRows(FinanceImportPreviewRequest request, String tenantId,
                                                     String platform, PlatformIntegration integration,
                                                     List<String> warnings) {
        if (request.getRawRows() != null && !request.getRawRows().isEmpty()) {
            return request.getRawRows();
        }
        if (StringUtils.hasText(request.getContent())) {
            return importSourceParser.parse(request.getContent());
        }
        boolean useSample = Boolean.TRUE.equals(request.getUseSampleData());
        if (!useSample && integration == null) {
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取财务数据；可勾选「使用示例数据」或改用文件上传导入");
        }
        FinanceFetchRequest fetchRequest = new FinanceFetchRequest(
                tenantId,
                platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                request.getExternalTransactionId(),
                parseDateTime(request.getUpdatedFrom()),
                parseDateTime(request.getUpdatedTo()),
                request.getLimit() == null ? 50 : request.getLimit(),
                request.getPage() == null ? 1 : request.getPage());
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformFinanceFetcher fetcher = fetcherRegistry.financeFetcher(credential, useSample);
        if (useSample) {
            warnings.add("当前使用平台示例数据，正式环境请关闭该选项以调用真实平台接口");
        }
        return fetcher.fetch(fetchRequest, credential);
    }

    private PlatformIntegration resolveIntegration(String integrationId) {
        if (!StringUtils.hasText(integrationId)) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(integrationId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("平台集成ID格式不正确: " + integrationId);
        }
        return platformIntegrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("平台集成不存在: " + integrationId));
    }

    private String resolvePlatform(String platform, PlatformIntegration integration) {
        if (StringUtils.hasText(platform)) {
            return platform.trim().toUpperCase();
        }
        if (integration != null && integration.getPlatformName() != null) {
            return integration.getPlatformName().name();
        }
        return FILE_PLATFORM;
    }

    private String resolveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT_ID;
    }

    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), INPUT_FORMATTER);
        } catch (Exception e) {
            throw new ValidationException("时间格式不正确（应为 yyyy-MM-dd HH:mm:ss）: " + text);
        }
    }

    private List<FinanceFieldMapping> toDomainMappings(List<FieldMappingDto> dtos, String tenantId, String platform) {
        List<FinanceFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (FieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            FinanceImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = FinanceImportTargetField.valueOf(dto.getTargetField().trim());
                } catch (IllegalArgumentException e) {
                    throw new ValidationException("不支持的目标字段: " + dto.getTargetField());
                }
            }
            FieldTransform transform = FieldTransform.NONE;
            if (StringUtils.hasText(dto.getTransform())) {
                try {
                    transform = FieldTransform.valueOf(dto.getTransform().trim());
                } catch (IllegalArgumentException e) {
                    throw new ValidationException("不支持的转换规则: " + dto.getTransform());
                }
            }
            FinanceFieldMapping mapping = FinanceFieldMapping.create(tenantId, platform, dto.getSourceField().trim(),
                    targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<FieldMappingDto> toMappingDtos(List<FinanceFieldMapping> mappings) {
        List<FieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (FinanceFieldMapping mapping : mappings) {
            FieldMappingDto dto = new FieldMappingDto();
            dto.setSourceField(mapping.getSourceField());
            dto.setTargetField(mapping.getTargetField() == null ? null : mapping.getTargetField().name());
            dto.setTransform(mapping.getTransform() == null ? FieldTransform.NONE.name() : mapping.getTransform().name());
            dto.setDefaultValue(mapping.getDefaultValue());
            dto.setEnabled(mapping.isEnabled());
            dtos.add(dto);
        }
        return dtos;
    }

    private FinanceImportRowDto toRowDto(ImportedFinanceRow row) {
        FinanceImportRowDto dto = new FinanceImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setExternalTransactionId(row.externalTransactionId());
        dto.setType(row.type() == null ? null : row.type().name());
        dto.setAmount(row.amount());
        dto.setCurrency(row.currency());
        dto.setReferenceId(row.referenceId());
        dto.setReferenceType(row.referenceType());
        dto.setDescription(row.description());
        dto.setStatus(row.status() == null ? TransactionStatus.SUCCESS.name() : row.status().name());
        dto.setOccurredAt(row.occurredAt());
        return dto;
    }

    private List<FinanceImportErrorDto> toErrorDtos(List<FinanceImportError> errors) {
        List<FinanceImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (FinanceImportError error : errors) {
            FinanceImportErrorDto dto = new FinanceImportErrorDto();
            dto.setRowNumber(error.getRowNumber());
            dto.setExternalTransactionId(error.getExternalTransactionId());
            dto.setField(error.getField());
            dto.setMessage(error.getMessage());
            dtos.add(dto);
        }
        return dtos;
    }

    private FinanceImportJobDto toJobDto(FinanceImportJob job, boolean includeIds) {
        FinanceImportJobDto dto = new FinanceImportJobDto();
        dto.setJobId(job.getId());
        dto.setTenantId(job.getTenantId());
        dto.setPlatform(job.getPlatform());
        dto.setIntegrationId(job.getIntegrationId());
        dto.setFileName(job.getFileName());
        dto.setStatus(job.getStatus() == null ? ImportJobStatus.PENDING.name() : job.getStatus().name());
        dto.setStatusLabel(job.getStatus() == null ? ImportJobStatus.PENDING.getLabel() : job.getStatus().getLabel());
        dto.setTotalCount(job.getTotalCount());
        dto.setSuccessCount(job.getSuccessCount());
        dto.setFailureCount(job.getFailureCount());
        dto.setSkippedCount(job.getSkippedCount());
        dto.setMessage(job.getMessage());
        dto.setStartedAt(format(job.getStartedAt()));
        dto.setFinishedAt(format(job.getFinishedAt()));
        dto.setErrors(toErrorDtos(job.getErrors()));
        if (includeIds) {
            dto.setCreatedTransactionIds(job.getCreatedTransactionIds());
            dto.setUpdatedTransactionIds(job.getUpdatedTransactionIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(FinanceImportJob job) {
        financeImportJobRepository.save(job);
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
