package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouseImportJob;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseImportError;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.ShopWarehouseFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ImportedShopWarehouseRow;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ShopWarehouseImportTargetField;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseFieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseFieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportRowDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第三方电商平台店铺仓库导入应用服务。
 * <p>
 * 完整链路：店铺仓库数据获取 -> 字段映射 -> 批量导入 -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class WarehouseImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    public static final String DEFAULT_TENANT_ID = "default-tenant";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShopWarehouseFieldMappingService shopWarehouseFieldMappingService;
    private final ShopWarehouseImportJobRepository shopWarehouseImportJobRepository;
    private final ShopWarehouseFieldMappingRepository shopWarehouseFieldMappingRepository;
    private final ShopWarehouseRepository shopWarehouseRepository;
    private final WarehouseRepository warehouseRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public WarehouseImportApplicationService(ShopWarehouseFieldMappingService shopWarehouseFieldMappingService,
                                             ShopWarehouseImportJobRepository shopWarehouseImportJobRepository,
                                             ShopWarehouseFieldMappingRepository shopWarehouseFieldMappingRepository,
                                             ShopWarehouseRepository shopWarehouseRepository,
                                             WarehouseRepository warehouseRepository,
                                             PlatformIntegrationRepository platformIntegrationRepository,
                                             ImportSourceParser importSourceParser,
                                             PlatformFetcherRegistry fetcherRegistry,
                                             PlatformCredentials platformCredentials) {
        this.shopWarehouseFieldMappingService = shopWarehouseFieldMappingService;
        this.shopWarehouseImportJobRepository = shopWarehouseImportJobRepository;
        this.shopWarehouseFieldMappingRepository = shopWarehouseFieldMappingRepository;
        this.shopWarehouseRepository = shopWarehouseRepository;
        this.warehouseRepository = warehouseRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始数据并完成字段映射，不写入店铺仓库绑定
     */
    public ShopWarehouseImportPreviewResponse preview(ShopWarehouseImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = shopWarehouseFieldMappingService.detectSourceFields(rawRows);

        List<ShopWarehouseFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : shopWarehouseFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<ShopWarehouseImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<ShopWarehouseImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = shopWarehouseFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            ShopWarehouseImportRowDto dto = toRowDto(result.row());
            dto.setErrors(toErrorDtos(result.errors()));
            rows.add(dto);
            rowErrors.addAll(toErrorDtos(result.errors()));
        }

        if (mappings.isEmpty()) {
            warnings.add("未能识别出任何字段映射，请在字段映射中手工指定平台字段与系统字段的对应关系");
        }

        ShopWarehouseImportPreviewResponse response = new ShopWarehouseImportPreviewResponse();
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
    @Transactional
    public ShopWarehouseImportJobDto importShopWarehouses(ShopWarehouseImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();

        List<ShopWarehouseImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            ShopWarehouseImportPreviewRequest previewRequest = new ShopWarehouseImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setKeyword(request.getKeyword());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            ShopWarehouseImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        ShopWarehouseImportJob job = ShopWarehouseImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());
        persistJob(job);

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                ShopWarehouseImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, integration, updateExisting, job);
                } catch (Exception e) {
                    log.warn("[ShopWarehouseImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(ShopWarehouseImportError.of(rowNumber, row.getShopCode(),
                            row.getPlatformWarehouseId(), null, normalizeErrorMessage(e)));
                }
            }
        }
        job.complete();
        persistJob(job);
        return toJobDto(job, true);
    }

    /**
     * 查询导入任务详情
     */
    public ShopWarehouseImportJobDto getJob(String jobId) {
        return shopWarehouseImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<ShopWarehouseImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<ShopWarehouseImportJob> jobs =
                shopWarehouseImportJobRepository.findByTenantId(resolveTenant(tenantId), page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<ShopWarehouseFieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(shopWarehouseFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform.trim().toUpperCase()));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<ShopWarehouseFieldMappingDto> saveMappings(ShopWarehouseFieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform().trim().toUpperCase() : FILE_PLATFORM;
        List<ShopWarehouseFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (ShopWarehouseFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        shopWarehouseFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<ShopWarehouseFieldMapping> saved = shopWarehouseFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (ShopWarehouseImportTargetField field : ShopWarehouseImportTargetField.values()) {
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
     * 生成标准CSV导入模板
     */
    public String csvTemplate() {
        return String.join(",", List.of("shopCode", "shopName", "platformWarehouseId", "platformWarehouseName",
                "warehouseCode", "address", "isDefault", "active")) + "\n"
                + "AMZ-STORE-01,亚马逊店铺1,AMZ-WH-01,Amazon FBA 仓1,WH-001,美国加州1号仓,true,true\n"
                + "AMZ-STORE-01,亚马逊店铺1,AMZ-WH-02,Amazon FBA 仓2,WH-002,美国加州2号仓,false,true\n";
    }

    private void processRow(ShopWarehouseImportRowDto row, int rowNumber, String tenantId, String platform,
                            PlatformIntegration integration, boolean updateExisting, ShopWarehouseImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            ShopWarehouseImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        String platformWarehouseId = row.getPlatformWarehouseId();
        if (!StringUtils.hasText(platformWarehouseId)) {
            throw new ValidationException("平台仓库ID为空");
        }

        // 解析绑定的本地仓库
        Warehouse localWarehouse = null;
        if (StringUtils.hasText(row.getWarehouseCode())) {
            localWarehouse = warehouseRepository.findByWarehouseCode(row.getWarehouseCode().trim())
                    .orElseThrow(() -> new ValidationException(
                            "本地仓库编码不存在: " + row.getWarehouseCode() + "，请先创建对应仓库"));
        }

        var existing = shopWarehouseRepository
                .findByTenantIdAndPlatformAndPlatformWarehouseId(tenantId, platform, platformWarehouseId.trim());
        if (existing.isPresent() && !updateExisting) {
            job.recordSkipped(ShopWarehouseImportError.of(rowNumber, row.getShopCode(), platformWarehouseId,
                    ShopWarehouseImportTargetField.PLATFORM_WAREHOUSE_ID.name(),
                    "平台仓库ID已存在且未开启覆盖更新，已跳过"));
            return;
        }

        ShopWarehouse target = existing.orElseGet(() -> ShopWarehouse.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()),
                row.getShopCode(), row.getShopName(), platformWarehouseId,
                row.getPlatformWarehouseName(), row.getAddress(),
                Boolean.TRUE.equals(row.getIsDefault())));

        target.updateFromPlatform(row.getShopName(), row.getPlatformWarehouseName(), row.getAddress(),
                row.getIsDefault(), integration == null ? null : String.valueOf(integration.getId()));
        if (Boolean.TRUE.equals(row.getActive())) {
            target.changeActive(true);
        } else if (row.getActive() != null) {
            target.changeActive(false);
        }
        if (localWarehouse != null) {
            target.bindToLocalWarehouse(localWarehouse.getWarehouseId(), localWarehouse.getWarehouseCode());
        } else if (row.getIsDefault() != null && row.getIsDefault() && !target.isBound()) {
            // 默认仓库行未指定本地仓库编码时给出失败提示，避免静默丢失绑定
            throw new ValidationException("默认仓库未绑定本地仓库编码，请在数据中提供 warehouseCode");
        }
        shopWarehouseRepository.save(target);
        job.recordSuccess(target.getId(), existing.isPresent());
    }

    private List<Map<String, Object>> resolveRawRows(ShopWarehouseImportPreviewRequest request, String tenantId,
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
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取店铺仓库；可勾选「使用示例数据」或改用文件上传导入");
        }
        PlatformWarehouseFetchRequest fetchRequest = new PlatformWarehouseFetchRequest(
                tenantId,
                platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                request.getKeyword(),
                request.getLimit() == null ? 50 : request.getLimit(),
                1);
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformWarehouseFetcher fetcher = fetcherRegistry.warehouseFetcher(credential, useSample);
        if (useSample) {
            warnings.add("当前使用平台示例数据，正式环境请勾选关闭以调用真实平台接口");
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
        if (StringUtils.hasText(platform) && !FILE_PLATFORM.equalsIgnoreCase(platform)) {
            return platform.trim().toUpperCase();
        }
        if (integration != null && integration.getPlatformName() != null) {
            return integration.getPlatformName().name();
        }
        return FILE_PLATFORM;
    }

    private String resolveTenant(String tenantId) {
        return TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID);
    }

    private List<ShopWarehouseFieldMapping> toDomainMappings(List<ShopWarehouseFieldMappingDto> dtos,
                                                             String tenantId, String platform) {
        List<ShopWarehouseFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (ShopWarehouseFieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            ShopWarehouseImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = ShopWarehouseImportTargetField.valueOf(dto.getTargetField().trim());
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
            ShopWarehouseFieldMapping mapping = ShopWarehouseFieldMapping.create(tenantId, platform,
                    dto.getSourceField().trim(), targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<ShopWarehouseFieldMappingDto> toMappingDtos(List<ShopWarehouseFieldMapping> mappings) {
        List<ShopWarehouseFieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (ShopWarehouseFieldMapping mapping : mappings) {
            ShopWarehouseFieldMappingDto dto = new ShopWarehouseFieldMappingDto();
            dto.setSourceField(mapping.getSourceField());
            dto.setTargetField(mapping.getTargetField() == null ? null : mapping.getTargetField().name());
            dto.setTransform(mapping.getTransform() == null ? FieldTransform.NONE.name() : mapping.getTransform().name());
            dto.setDefaultValue(mapping.getDefaultValue());
            dto.setEnabled(mapping.isEnabled());
            dtos.add(dto);
        }
        return dtos;
    }

    private ShopWarehouseImportRowDto toRowDto(ImportedShopWarehouseRow row) {
        ShopWarehouseImportRowDto dto = new ShopWarehouseImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setShopCode(row.shopCode());
        dto.setShopName(row.shopName());
        dto.setPlatform(row.platform());
        dto.setPlatformWarehouseId(row.platformWarehouseId());
        dto.setPlatformWarehouseName(row.platformWarehouseName());
        dto.setWarehouseCode(row.warehouseCode());
        dto.setAddress(row.address());
        dto.setIsDefault(row.isDefault());
        dto.setActive(row.active());
        return dto;
    }

    private List<ShopWarehouseImportErrorDto> toErrorDtos(List<ShopWarehouseImportError> errors) {
        List<ShopWarehouseImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (ShopWarehouseImportError error : errors) {
            dtos.add(toErrorDto(error));
        }
        return dtos;
    }

    private ShopWarehouseImportErrorDto toErrorDto(ShopWarehouseImportError error) {
        ShopWarehouseImportErrorDto dto = new ShopWarehouseImportErrorDto();
        dto.setRowNumber(error.getRowNumber());
        dto.setShopCode(error.getShopCode());
        dto.setPlatformWarehouseId(error.getPlatformWarehouseId());
        dto.setField(error.getField());
        dto.setMessage(error.getMessage());
        return dto;
    }

    private ShopWarehouseImportJobDto toJobDto(ShopWarehouseImportJob job, boolean includeIds) {
        ShopWarehouseImportJobDto dto = new ShopWarehouseImportJobDto();
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
            dto.setCreatedShopWarehouseIds(job.getCreatedShopWarehouseIds());
            dto.setUpdatedShopWarehouseIds(job.getUpdatedShopWarehouseIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(ShopWarehouseImportJob job) {
        shopWarehouseImportJobRepository.save(job);
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
