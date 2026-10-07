package site.hexaarch.ecommerce.logistics.application.service.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsImportJob;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsImportError;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.ImportedLogisticsRow;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRowDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportTraceDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方电商平台物流信息导入应用服务。
 * <p>
 * 完整链路：店铺物流数据获取 -> 字段映射 -> 批量导入（关联订单/渠道） -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class LogisticsImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    public static final String DEFAULT_TENANT_ID = "default-tenant";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogisticsOrderRepository logisticsOrderRepository;
    private final LogisticsFieldMappingService logisticsFieldMappingService;
    private final LogisticsFieldMappingRepository logisticsFieldMappingRepository;
    private final LogisticsImportJobRepository logisticsImportJobRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final DomainEventPublisher domainEventPublisher;
    private final LogisticsOrderResolver logisticsOrderResolver;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public LogisticsImportApplicationService(LogisticsOrderRepository logisticsOrderRepository,
                                             LogisticsFieldMappingService logisticsFieldMappingService,
                                             LogisticsFieldMappingRepository logisticsFieldMappingRepository,
                                             LogisticsImportJobRepository logisticsImportJobRepository,
                                             PlatformIntegrationRepository platformIntegrationRepository,
                                             ImportSourceParser importSourceParser,
                                             DomainEventPublisher domainEventPublisher,
                                             LogisticsOrderResolver logisticsOrderResolver,
                                             PlatformFetcherRegistry fetcherRegistry,
                                             PlatformCredentials platformCredentials) {
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.logisticsFieldMappingService = logisticsFieldMappingService;
        this.logisticsFieldMappingRepository = logisticsFieldMappingRepository;
        this.logisticsImportJobRepository = logisticsImportJobRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.domainEventPublisher = domainEventPublisher;
        this.logisticsOrderResolver = logisticsOrderResolver;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始物流数据并完成字段映射，不写入物流单库
     */
    public LogisticsImportPreviewResponse preview(LogisticsImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = logisticsFieldMappingService.detectSourceFields(rawRows);

        List<LogisticsFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : logisticsFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<LogisticsImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<LogisticsImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = logisticsFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            LogisticsImportRowDto dto = toRowDto(result.row());
            dto.setErrors(toErrorDtos(result.errors()));
            rows.add(dto);
            rowErrors.addAll(toErrorDtos(result.errors()));
        }

        if (mappings.isEmpty()) {
            warnings.add("未能识别出任何字段映射，请在字段映射中手工指定平台字段与系统物流字段的对应关系");
        }
        if (!rowErrors.isEmpty()) {
            warnings.add("存在 " + rowErrors.size() + " 条字段级错误，导入时将跳过对应行");
        }

        LogisticsImportPreviewResponse response = new LogisticsImportPreviewResponse();
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
    public LogisticsImportJobDto importLogistics(LogisticsImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();
        boolean idempotency = request.getEnableIdempotency() == null || request.getEnableIdempotency();

        List<LogisticsImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            LogisticsImportPreviewRequest previewRequest = new LogisticsImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setOrderNumber(request.getOrderNumber());
            previewRequest.setTrackingNumber(request.getTrackingNumber());
            previewRequest.setShippedFrom(request.getShippedFrom());
            previewRequest.setShippedTo(request.getShippedTo());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setPage(request.getPage());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            LogisticsImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        LogisticsImportJob job = LogisticsImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());
        persistJob(job);

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                LogisticsImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, updateExisting, idempotency, job);
                } catch (Exception e) {
                    log.warn("[LogisticsImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(LogisticsImportError.of(rowNumber, row.getExternalLogisticsId(),
                            row.getOrderNumber(), null, normalizeErrorMessage(e)));
                }
            }
        }
        job.complete();
        persistJob(job);
        return toJobDto(job, true);
    }

    /**
     * 从指定平台集成同步物流数据（供「平台集成-同步物流」入口调用）
     */
    public LogisticsImportJobDto syncFromPlatform(String integrationId, String tenantId, String orderNumber,
                                                  String trackingNumber, String shippedFrom, String shippedTo,
                                                  Integer limit, Boolean useSampleData) {
        LogisticsImportRequest request = new LogisticsImportRequest();
        request.setIntegrationId(integrationId);
        request.setTenantId(tenantId);
        request.setOrderNumber(orderNumber);
        request.setTrackingNumber(trackingNumber);
        request.setShippedFrom(shippedFrom);
        request.setShippedTo(shippedTo);
        request.setLimit(limit);
        request.setUseSampleData(useSampleData);
        request.setUpdateExisting(Boolean.TRUE);
        request.setEnableIdempotency(Boolean.TRUE);
        return importLogistics(request);
    }

    /**
     * 查询导入任务详情
     */
    public LogisticsImportJobDto getJob(String jobId) {
        return logisticsImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<LogisticsImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<LogisticsImportJob> jobs = logisticsImportJobRepository
                .findByTenantId(resolveTenant(tenantId), page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<FieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(logisticsFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<FieldMappingDto> saveMappings(FieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : FILE_PLATFORM;
        List<LogisticsFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (LogisticsFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        logisticsFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<LogisticsFieldMapping> saved = logisticsFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (LogisticsImportTargetField field : LogisticsImportTargetField.values()) {
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
     * 物流状态字典，供导入时状态映射与前端下拉使用
     */
    public List<TargetFieldDto> logisticsStatuses() {
        List<TargetFieldDto> statuses = new ArrayList<>();
        for (LogisticsStatus status : LogisticsStatus.values()) {
            statuses.add(new TargetFieldDto(status.name(), status.getDescription(), Boolean.FALSE));
        }
        return statuses;
    }

    /**
     * 生成标准CSV导入模板
     */
    public String csvTemplate() {
        String header = String.join(",", List.of("externalLogisticsId", "trackingNumber", "orderNumber",
                "externalOrderId", "channelCode", "carrier", "status", "trackingUrl", "labelUrl", "labelFormat",
                "barcode", "shippedAt", "estimatedArrivalAt", "deliveredAt", "totalFee", "currency", "recipient",
                "phone", "country", "province", "city", "street", "zipCode", "traces"));
        String traceJson = "[{\"time\":\"2026-09-01 10:00:00\",\"status\":\"COLLECTED\","
                + "\"description\":\"已揽收\",\"location\":\"深圳转运中心\"}]";
        // CSV 中的 JSON 数组需要用双引号包裹，并把内部双引号转义成两个双引号
        String escapedTrace = "\"" + traceJson.replace("\"", "\"\"") + "\"";
        String sample = String.join(",", List.of("AMAZON-SHIP-001", "SF1234567890", "", "AMAZON-ORD-001", "SF",
                "顺丰速运", "Shipped", "https://track.example.com/1", "", "", "SF1234567890", "2026-09-01 10:00:00",
                "2026-09-06 10:00:00", "", "12.50", "CNY", "张三", "13800138000", "中国", "广东省", "深圳市",
                "南山区科技园路1号", "518000")) + "," + escapedTrace;
        return header + "\n" + sample + "\n";
    }

    private void processRow(LogisticsImportRowDto row, int rowNumber, String tenantId, String platform,
                            boolean updateExisting, boolean idempotency, LogisticsImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            LogisticsImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        String externalLogisticsId = row.getExternalLogisticsId();
        if (!StringUtils.hasText(externalLogisticsId)) {
            throw new ValidationException("平台物流单号为空，无法建立幂等关联");
        }

        Order order = logisticsOrderResolver.resolveOrder(tenantId, null, row.getOrderNumber(),
                row.getExternalOrderId(), platform);
        var channel = resolveChannel(row);

        LogisticsStatus targetStatus = parseStatus(row.getStatus());
        LogisticsOrder existing = idempotency
                ? logisticsOrderRepository.findByPlatformExternalId(tenantId, platform, externalLogisticsId).orElse(null)
                : null;

        boolean isUpdate = existing != null;
        LogisticsOrder target;
        if (isUpdate) {
            if (!updateExisting) {
                job.recordSkipped(LogisticsImportError.of(rowNumber, externalLogisticsId, row.getOrderNumber(),
                        LogisticsImportTargetField.EXTERNAL_LOGISTICS_ID.name(),
                        "平台物流单号已存在且未开启覆盖更新，已跳过"));
                return;
            }
            if (isFinal(existing.getLogisticsStatus()) && existing.getLogisticsStatus() != targetStatus) {
                job.recordSkipped(LogisticsImportError.of(rowNumber, externalLogisticsId, row.getOrderNumber(),
                        LogisticsImportTargetField.STATUS.name(),
                        "物流单已处于终态[" + existing.getLogisticsStatus().getDescription() + "]，不再回退更新"));
                return;
            }
            target = existing;
        } else {
            target = LogisticsOrder.create(tenantId, order.getId(), channel.getChannelId(),
                    row.getTrackingNumber(), row.getCarrier());
            target.bindPlatformSource(platform, externalLogisticsId);
        }

        target.assignTrackingNumber(row.getTrackingNumber());
        if (StringUtils.hasText(row.getCarrier())) {
            target.setCarrier(row.getCarrier());
        }
        if (StringUtils.hasText(row.getTrackingUrl())) {
            target.setTrackingUrl(row.getTrackingUrl());
        }
        if (row.getTotalFee() != null && row.getTotalFee().compareTo(BigDecimal.ZERO) >= 0) {
            target.setLogisticsFee(LogisticsFee.builder()
                    .totalFee(row.getTotalFee())
                    .currency(StringUtils.hasText(row.getCurrency()) ? row.getCurrency() : "CNY")
                    .build());
        }
        target.recordShippedAt(row.getShippedAt());
        target.recordEstimatedArrivalAt(row.getEstimatedArrivalAt());
        target.recordDeliveredAt(row.getDeliveredAt());
        if (StringUtils.hasText(row.getLabelUrl()) || StringUtils.hasText(row.getBarcode())) {
            target.attachLabel(buildLabel(row));
        }
        if (row.getTraces() != null) {
            for (LogisticsImportTraceDto trace : row.getTraces()) {
                if (trace == null) {
                    continue;
                }
                target.appendTrace(LogisticsTrace.of(trace.getTime(), trace.getStatus(), trace.getDescription(),
                        trace.getLocation()));
            }
        }
        if (targetStatus != null && target.getLogisticsStatus() != targetStatus) {
            target.updateStatus(targetStatus, "平台物流数据同步");
        }

        LogisticsOrder saved = logisticsOrderRepository.save(target);
        domainEventPublisher.publishEventsFrom(saved);
        job.recordSuccess(saved.getLogisticsOrderId(), isUpdate);
    }

    /**
     * 渠道匹配：优先按渠道编码，其次按承运商名称匹配；均无法匹配时报错提示维护渠道
     */
    private site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel resolveChannel(
            LogisticsImportRowDto row) {
        var byCode = logisticsOrderResolver.findChannelQuietly(row.getChannelCode());
        if (byCode != null) {
            return byCode;
        }
        var byCarrier = logisticsOrderResolver.findChannelQuietly(row.getCarrier());
        if (byCarrier != null) {
            return byCarrier;
        }
        throw new ValidationException("无法匹配物流渠道（渠道编码=" + row.getChannelCode() + "，承运商="
                + row.getCarrier() + "），请先在物流渠道中维护该渠道");
    }

    private LogisticsLabel buildLabel(LogisticsImportRowDto row) {
        return LogisticsLabel.builder()
                .trackingNumber(StringUtils.hasText(row.getTrackingNumber())
                        ? row.getTrackingNumber() : row.getExternalLogisticsId())
                .recipientInfo(LogisticsLabel.RecipientInfo.builder()
                        .name(row.getRecipient())
                        .phone(row.getPhone())
                        .address(fullAddress(row))
                        .build())
                .barcode(row.getBarcode())
                .labelUrl(row.getLabelUrl())
                .labelFormat(StringUtils.hasText(row.getLabelFormat()) ? row.getLabelFormat() : "PDF")
                .build();
    }

    private String fullAddress(LogisticsImportRowDto row) {
        List<String> parts = new ArrayList<>();
        for (String part : List.of(nvl(row.getCountry()), nvl(row.getProvince()), nvl(row.getCity()),
                nvl(row.getStreet()))) {
            if (StringUtils.hasText(part)) {
                parts.add(part);
            }
        }
        String address = String.join(" ", parts);
        return StringUtils.hasText(address) ? address : null;
    }

    private boolean isFinal(LogisticsStatus status) {
        return status == LogisticsStatus.DELIVERED || status == LogisticsStatus.CANCELLED;
    }

    private LogisticsStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LogisticsStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return logisticsFieldMappingService.toLogisticsStatus(value);
        }
    }

    private List<Map<String, Object>> resolveRawRows(LogisticsImportPreviewRequest request, String tenantId,
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
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取物流数据；可勾选「使用示例数据」或改用文件上传导入");
        }
        PlatformLogisticsFetchRequest fetchRequest = new PlatformLogisticsFetchRequest(
                tenantId,
                platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                request.getOrderNumber(),
                request.getTrackingNumber(),
                parseDateTime(request.getShippedFrom()),
                parseDateTime(request.getShippedTo()),
                request.getLimit() == null ? 50 : request.getLimit(),
                request.getPage() == null ? 1 : request.getPage());
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformLogisticsFetcher fetcher = fetcherRegistry.logisticsFetcher(credential, useSample);
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

    private List<LogisticsFieldMapping> toDomainMappings(List<FieldMappingDto> dtos, String tenantId,
                                                         String platform) {
        List<LogisticsFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (FieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            LogisticsImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = LogisticsImportTargetField.valueOf(dto.getTargetField().trim());
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
            LogisticsFieldMapping mapping = LogisticsFieldMapping.create(tenantId, platform,
                    dto.getSourceField().trim(), targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<FieldMappingDto> toMappingDtos(List<LogisticsFieldMapping> mappings) {
        List<FieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (LogisticsFieldMapping mapping : mappings) {
            FieldMappingDto dto = new FieldMappingDto();
            dto.setSourceField(mapping.getSourceField());
            dto.setTargetField(mapping.getTargetField() == null ? null : mapping.getTargetField().name());
            dto.setTransform(mapping.getTransform() == null ? FieldTransform.NONE.name()
                    : mapping.getTransform().name());
            dto.setDefaultValue(mapping.getDefaultValue());
            dto.setEnabled(mapping.isEnabled());
            dtos.add(dto);
        }
        return dtos;
    }

    private LogisticsImportRowDto toRowDto(ImportedLogisticsRow row) {
        LogisticsImportRowDto dto = new LogisticsImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setExternalLogisticsId(row.externalLogisticsId());
        dto.setTrackingNumber(row.trackingNumber());
        dto.setOrderNumber(row.orderNumber());
        dto.setExternalOrderId(row.externalOrderId());
        dto.setChannelCode(row.channelCode());
        dto.setCarrier(row.carrier());
        dto.setStatus(row.status() == null ? LogisticsStatus.PENDING.name() : row.status().name());
        dto.setTrackingUrl(row.trackingUrl());
        dto.setLabelUrl(row.labelUrl());
        dto.setLabelFormat(row.labelFormat());
        dto.setBarcode(row.barcode());
        dto.setShippedAt(row.shippedAt());
        dto.setEstimatedArrivalAt(row.estimatedArrivalAt());
        dto.setDeliveredAt(row.deliveredAt());
        dto.setTotalFee(row.totalFee());
        dto.setCurrency(row.currency());
        dto.setRecipient(row.recipient());
        dto.setPhone(row.phone());
        dto.setCountry(row.country());
        dto.setProvince(row.province());
        dto.setCity(row.city());
        dto.setStreet(row.street());
        dto.setZipCode(row.zipCode());
        List<LogisticsImportTraceDto> traces = new ArrayList<>();
        if (row.traces() != null) {
            for (LogisticsTrace trace : row.traces()) {
                LogisticsImportTraceDto traceDto = new LogisticsImportTraceDto();
                traceDto.setTime(trace.time());
                traceDto.setStatus(trace.status());
                traceDto.setDescription(trace.description());
                traceDto.setLocation(trace.location());
                traces.add(traceDto);
            }
        }
        dto.setTraces(traces);
        return dto;
    }

    private List<LogisticsImportErrorDto> toErrorDtos(List<LogisticsImportError> errors) {
        List<LogisticsImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (LogisticsImportError error : errors) {
            LogisticsImportErrorDto dto = new LogisticsImportErrorDto();
            dto.setRowNumber(error.getRowNumber());
            dto.setExternalLogisticsId(error.getExternalLogisticsId());
            dto.setOrderNumber(error.getOrderNumber());
            dto.setField(error.getField());
            dto.setMessage(error.getMessage());
            dtos.add(dto);
        }
        return dtos;
    }

    private LogisticsImportJobDto toJobDto(LogisticsImportJob job, boolean includeIds) {
        LogisticsImportJobDto dto = new LogisticsImportJobDto();
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
            dto.setCreatedLogisticsOrderIds(job.getCreatedLogisticsOrderIds());
            dto.setUpdatedLogisticsOrderIds(job.getUpdatedLogisticsOrderIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(LogisticsImportJob job) {
        logisticsImportJobRepository.save(job);
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
