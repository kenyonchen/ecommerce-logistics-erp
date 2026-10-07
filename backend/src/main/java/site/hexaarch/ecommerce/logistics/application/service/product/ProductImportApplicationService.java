package site.hexaarch.ecommerce.logistics.application.service.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateProductCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateSkuCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.ProductAttributeCommand;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.ProductImportJob;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductImportError;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportedProductRow;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductAttribute;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRowDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方电商平台商品导入应用服务。
 * <p>
 * 完整链路：商品数据获取 -> 字段映射 -> 批量导入 -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class ProductImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductApplicationService productApplicationService;
    private final ProductFieldMappingService productFieldMappingService;
    private final ProductImportJobRepository productImportJobRepository;
    private final ProductFieldMappingRepository productFieldMappingRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public ProductImportApplicationService(ProductApplicationService productApplicationService,
                                           ProductFieldMappingService productFieldMappingService,
                                           ProductImportJobRepository productImportJobRepository,
                                           ProductFieldMappingRepository productFieldMappingRepository,
                                           PlatformIntegrationRepository platformIntegrationRepository,
                                           ImportSourceParser importSourceParser,
                                           PlatformFetcherRegistry fetcherRegistry,
                                           PlatformCredentials platformCredentials) {
        this.productApplicationService = productApplicationService;
        this.productFieldMappingService = productFieldMappingService;
        this.productImportJobRepository = productImportJobRepository;
        this.productFieldMappingRepository = productFieldMappingRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始数据并完成字段映射，不写入商品库
     */
    public ProductImportPreviewResponse preview(ProductImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = productFieldMappingService.detectSourceFields(rawRows);

        List<ProductFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : productFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<ProductImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<ProductImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = productFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            ProductImportRowDto dto = toRowDto(result.row());
            dto.setErrors(toErrorDtos(result.errors()));
            rows.add(dto);
            rowErrors.addAll(toErrorDtos(result.errors()));
        }

        if (mappings.isEmpty()) {
            warnings.add("未能识别出任何字段映射，请在字段映射中手工指定平台字段与系统字段的对应关系");
        }

        ProductImportPreviewResponse response = new ProductImportPreviewResponse();
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
    public ProductImportJobDto importProducts(ProductImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();
        boolean idempotency = request.getEnableIdempotency() == null || request.getEnableIdempotency();

        List<ProductImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            ProductImportPreviewRequest previewRequest = new ProductImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setKeyword(request.getKeyword());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            ProductImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        ProductImportJob job = ProductImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());
        persistJob(job);

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                ProductImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, updateExisting, idempotency, job);
                } catch (Exception e) {
                    log.warn("[ProductImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(ProductImportError.of(rowNumber, row.getExternalProductId(),
                            row.getSkuCode(), null, normalizeErrorMessage(e)));
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
    public ProductImportJobDto getJob(String jobId) {
        return productImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<ProductImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<ProductImportJob> jobs = productImportJobRepository.findByTenantId(tenantId, page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<FieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(productFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<FieldMappingDto> saveMappings(FieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : FILE_PLATFORM;
        List<ProductFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (ProductFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        productFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<ProductFieldMapping> saved = productFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (ImportTargetField field : ImportTargetField.values()) {
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
        return String.join(",", List.of("name", "skuCode", "categoryName", "sellingPrice", "costPrice",
                "currency", "weightInGrams", "attributes", "description")) + "\n"
                + "示例商品,SKU-001,ELECTRONICS,199.00,120.00,CNY,500,颜色:黑色,这是示例商品\n";
    }

    private void processRow(ProductImportRowDto row, int rowNumber, String tenantId, String platform,
                            boolean updateExisting, boolean idempotency, ProductImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            ProductImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        String name = row.getName();
        String skuCode = row.getSkuCode();
        if (!StringUtils.hasText(name)) {
            throw new ValidationException("商品名称为空");
        }
        if (!StringUtils.hasText(skuCode)) {
            throw new ValidationException("SKU编码为空");
        }

        Optional<Product> existingByExternalId = idempotency && StringUtils.hasText(row.getExternalProductId())
                ? productApplicationService.findProductBySourceId(tenantId, platform, row.getExternalProductId())
                : Optional.empty();

        Optional<Product> existingBySku = productApplicationService.findOptionalProductBySkuCode(skuCode);

        Product target;
        boolean isUpdate;
        if (existingByExternalId.isPresent()) {
            target = existingByExternalId.get();
            isUpdate = true;
        } else if (existingBySku.isPresent()) {
            if (!updateExisting) {
                job.recordSkipped(ProductImportError.of(rowNumber, row.getExternalProductId(), skuCode,
                        ImportTargetField.SKU_CODE.name(), "SKU编码已存在且未开启覆盖更新，已跳过"));
                return;
            }
            target = existingBySku.get();
            isUpdate = true;
        } else {
            target = null;
            isUpdate = false;
        }

        if (target == null) {
            CreateProductCommand command = new CreateProductCommand(
                    tenantId,
                    name.trim(),
                    row.getDescription(),
                    null,
                    row.getCategoryName(),
                    platform,
                    row.getExternalProductId(),
                    row.getActive() == null || row.getActive(),
                    List.of(toSkuCommand(row)));
            Product created = productApplicationService.createProduct(command);
            job.recordSuccess(created.getId(), false);
            return;
        }

        target.updateInfo(name.trim(), row.getDescription() != null ? row.getDescription() : target.getDescription());
        if (StringUtils.hasText(row.getCategoryName())) {
            target.updateCategory(productApplicationService.resolveCategory(null, row.getCategoryName()));
        }
        if (row.getActive() != null) {
            if (row.getActive()) {
                target.activate();
            } else {
                target.deactivate();
            }
        }
        // 复用已有SKU主键，避免同编码SKU先插后删导致的唯一约束冲突
        String existingSkuId = target.findSkuByCode(row.getSkuCode().trim())
                .map(SKU::getId)
                .orElseGet(() -> UUID.randomUUID().toString());
        target.replaceSKU(toSkuEntity(target.getId(), row, existingSkuId));
        Product saved = productApplicationService.saveImportedProduct(target);
        job.recordSuccess(saved.getId(), isUpdate);
    }

    private SKU toSkuEntity(String productId, ProductImportRowDto row, String skuId) {
        List<ProductAttribute> attributes = new ArrayList<>();
        if (row.getAttributes() != null) {
            row.getAttributes().forEach(attribute -> {
                if (attribute != null && StringUtils.hasText(attribute.getName())) {
                    attributes.add(ProductAttribute.builder()
                            .name(attribute.getName())
                            .value(attribute.getValue())
                            .build());
                }
            });
        }
        String currency = StringUtils.hasText(row.getCurrency()) ? row.getCurrency() : "CNY";
        return SKU.builder()
                .id(skuId)
                .skuCode(row.getSkuCode().trim())
                .productId(productId)
                .name(row.getSkuName())
                .price(ProductPrice.builder()
                        .sellingPrice(row.getSellingPrice() == null ? BigDecimal.ZERO : row.getSellingPrice())
                        .costPrice(row.getCostPrice() == null ? BigDecimal.ZERO : row.getCostPrice())
                        .currency(currency)
                        .build())
                .weightInGrams(row.getWeightInGrams())
                .volumeInCubicCentimeters(row.getVolumeInCubicCentimeters())
                .attributes(attributes)
                .active(row.getActive() == null || row.getActive())
                .build();
    }

    private CreateSkuCommand toSkuCommand(ProductImportRowDto row) {
        List<ProductAttributeCommand> attributes = new ArrayList<>();
        if (row.getAttributes() != null) {
            row.getAttributes().forEach(attribute -> {
                if (attribute != null && StringUtils.hasText(attribute.getName())) {
                    attributes.add(new ProductAttributeCommand(attribute.getName(), attribute.getValue()));
                }
            });
        }
        return new CreateSkuCommand(
                row.getSkuCode() == null ? null : row.getSkuCode().trim(),
                row.getSkuName(),
                row.getSellingPrice(),
                row.getCostPrice(),
                row.getCurrency(),
                row.getWeightInGrams(),
                row.getVolumeInCubicCentimeters(),
                attributes,
                row.getActive());
    }

    private List<Map<String, Object>> resolveRawRows(ProductImportPreviewRequest request, String tenantId,
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
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取商品；可勾选「使用示例数据」或改用文件上传导入");
        }
        PlatformProductFetchRequest fetchRequest = new PlatformProductFetchRequest(
                tenantId,
                platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                request.getKeyword(),
                request.getLimit() == null ? 50 : request.getLimit(),
                1);
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformProductFetcher fetcher = fetcherRegistry.productFetcher(credential, useSample);
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
        if (StringUtils.hasText(platform)) {
            return platform.trim().toUpperCase();
        }
        if (integration != null && integration.getPlatformName() != null) {
            return integration.getPlatformName().name();
        }
        return FILE_PLATFORM;
    }

    private String resolveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : ProductApplicationService.DEFAULT_TENANT_ID;
    }

    private List<ProductFieldMapping> toDomainMappings(List<FieldMappingDto> dtos, String tenantId, String platform) {
        List<ProductFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (FieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            ImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = ImportTargetField.valueOf(dto.getTargetField().trim());
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
            ProductFieldMapping mapping = ProductFieldMapping.create(tenantId, platform, dto.getSourceField().trim(),
                    targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<FieldMappingDto> toMappingDtos(List<ProductFieldMapping> mappings) {
        List<FieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (ProductFieldMapping mapping : mappings) {
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

    private ProductImportRowDto toRowDto(ImportedProductRow row) {
        ProductImportRowDto dto = new ProductImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setExternalProductId(row.externalProductId());
        dto.setExternalSkuId(row.externalSkuId());
        dto.setName(row.name());
        dto.setDescription(row.description());
        dto.setCategoryName(row.categoryName());
        dto.setSkuCode(row.skuCode());
        dto.setSkuName(row.skuName());
        dto.setSellingPrice(row.sellingPrice());
        dto.setCostPrice(row.costPrice());
        dto.setCurrency(row.currency());
        dto.setWeightInGrams(row.weightInGrams());
        dto.setVolumeInCubicCentimeters(row.volumeInCubicCentimeters());
        dto.setActive(row.active());
        List<site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductAttributeDto> attributes = new ArrayList<>();
        for (ProductAttribute attribute : row.attributes()) {
            attributes.add(new site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductAttributeDto(
                    attribute.getName(), attribute.getValue()));
        }
        dto.setAttributes(attributes);
        return dto;
    }

    private List<ProductImportErrorDto> toErrorDtos(List<ProductImportError> errors) {
        List<ProductImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (ProductImportError error : errors) {
            ProductImportErrorDto dto = new ProductImportErrorDto();
            dto.setRowNumber(error.getRowNumber());
            dto.setExternalProductId(error.getExternalProductId());
            dto.setSkuCode(error.getSkuCode());
            dto.setField(error.getField());
            dto.setMessage(error.getMessage());
            dtos.add(dto);
        }
        return dtos;
    }

    private ProductImportJobDto toJobDto(ProductImportJob job, boolean includeIds) {
        ProductImportJobDto dto = new ProductImportJobDto();
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
            dto.setCreatedProductIds(job.getCreatedProductIds());
            dto.setUpdatedProductIds(job.getUpdatedProductIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(ProductImportJob job) {
        productImportJobRepository.save(job);
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
