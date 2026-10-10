package site.hexaarch.ecommerce.logistics.application.service.customer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.CustomerImportJob;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerImportError;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.ImportedCustomerRow;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRowDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方电商平台客户导入应用服务。
 * <p>
 * 完整链路：客户数据获取（平台客户接口或从订单数据萃取买家）-> 字段映射 -> 批量导入 -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class CustomerImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    public static final String DEFAULT_TENANT_ID = CustomerApplicationService.DEFAULT_TENANT_ID;
    /**
     * 数据来源类型：平台客户（买家）接口
     */
    public static final String SOURCE_TYPE_CUSTOMERS = "CUSTOMERS";
    /**
     * 数据来源类型：从平台订单数据中萃取买家
     */
    public static final String SOURCE_TYPE_ORDERS = "ORDERS";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CustomerRepository customerRepository;
    private final CustomerApplicationService customerApplicationService;
    private final CustomerFieldMappingService customerFieldMappingService;
    private final CustomerFieldMappingRepository customerFieldMappingRepository;
    private final CustomerImportJobRepository customerImportJobRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public CustomerImportApplicationService(CustomerRepository customerRepository,
                                            CustomerApplicationService customerApplicationService,
                                            CustomerFieldMappingService customerFieldMappingService,
                                            CustomerFieldMappingRepository customerFieldMappingRepository,
                                            CustomerImportJobRepository customerImportJobRepository,
                                            PlatformIntegrationRepository platformIntegrationRepository,
                                            ImportSourceParser importSourceParser,
                                            PlatformFetcherRegistry fetcherRegistry,
                                            PlatformCredentials platformCredentials) {
        this.customerRepository = customerRepository;
        this.customerApplicationService = customerApplicationService;
        this.customerFieldMappingService = customerFieldMappingService;
        this.customerFieldMappingRepository = customerFieldMappingRepository;
        this.customerImportJobRepository = customerImportJobRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始数据并完成字段映射，不写入客户库
     */
    public CustomerImportPreviewResponse preview(CustomerImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = customerFieldMappingService.detectSourceFields(rawRows);

        List<CustomerFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : customerFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<CustomerImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<CustomerImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = customerFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            CustomerImportRowDto dto = toRowDto(result.row());
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

        CustomerImportPreviewResponse response = new CustomerImportPreviewResponse();
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
     * 批量导入：逐行处理，行级异常不影响其他行，最终产出导入任务与结果反馈。
     * 任务在 complete() 之后单次持久化，落库即为终态（含错误明细），避免 orphanRemoval 集合替换异常导致作业卡在 RUNNING。
     */
    @Transactional
    public CustomerImportJobDto importCustomers(CustomerImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();
        boolean idempotency = request.getEnableIdempotency() == null || request.getEnableIdempotency();

        List<CustomerImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            CustomerImportPreviewRequest previewRequest = new CustomerImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setSourceType(request.getSourceType());
            previewRequest.setKeyword(request.getKeyword());
            previewRequest.setOrderNumber(request.getOrderNumber());
            previewRequest.setUpdatedFrom(request.getUpdatedFrom());
            previewRequest.setUpdatedTo(request.getUpdatedTo());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setPage(request.getPage());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            CustomerImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        CustomerImportJob job = CustomerImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                CustomerImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, updateExisting, idempotency, job);
                } catch (Exception e) {
                    log.warn("[CustomerImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(CustomerImportError.of(rowNumber, row.getExternalBuyerId(),
                            row.getCustomerName(), null, normalizeErrorMessage(e)));
                }
            }
        }
        job.complete();
        persistJob(job);
        return toJobDto(job, true);
    }

    /**
     * 供平台集成「一键同步」调用：按集成配置从平台拉取买家（或从订单萃取买家）并直接完成批量导入，
     * 返回本次导入任务结果。
     *
     * @param integrationId 平台集成ID
     * @param tenantId      租户ID
     * @param sourceType    数据来源：CUSTOMERS（平台买家接口）| ORDERS（从订单萃取买家），为空默认 CUSTOMERS
     * @param keyword       平台侧买家过滤关键字
     * @param limit         拉取条数
     * @param useSampleData 是否使用示例数据（未联调时验证链路）
     * @return 导入任务结果
     */
    @Transactional
    public CustomerImportJobDto syncFromPlatform(String integrationId, String tenantId, String sourceType,
                                                 String keyword, Integer limit, Boolean useSampleData) {
        CustomerImportRequest request = new CustomerImportRequest();
        request.setIntegrationId(integrationId);
        request.setTenantId(tenantId);
        request.setSourceType(StringUtils.hasText(sourceType) ? sourceType : SOURCE_TYPE_CUSTOMERS);
        request.setKeyword(keyword);
        request.setLimit(limit);
        request.setUseSampleData(useSampleData);
        request.setUpdateExisting(Boolean.TRUE);
        request.setEnableIdempotency(Boolean.TRUE);
        return importCustomers(request);
    }

    /**
     * 查询导入任务详情
     */
    public CustomerImportJobDto getJob(String jobId) {
        return customerImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<CustomerImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<CustomerImportJob> jobs = customerImportJobRepository.findByTenantId(tenantId, page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<FieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(customerFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<FieldMappingDto> saveMappings(FieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : FILE_PLATFORM;
        List<CustomerFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (CustomerFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        customerFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<CustomerFieldMapping> saved = customerFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (CustomerImportTargetField field : CustomerImportTargetField.values()) {
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
        return String.join(",", List.of("externalBuyerId", "customerName", "email", "phone", "company",
                "country", "province", "city", "address")) + "\n"
                + "amz-buyer-001,张三,zhangsan@example.com,13800138000,示例公司,中国,广东省,深圳市,南山区科技园路1号\n";
    }

    private void processRow(CustomerImportRowDto row, int rowNumber, String tenantId, String platform,
                            boolean updateExisting, boolean idempotency, CustomerImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            CustomerImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        if (!StringUtils.hasText(row.getCustomerName())) {
            throw new ValidationException("客户名称为空，无法导入");
        }
        // 防御式校验：预览行级错误之外的兜底（如调用方直接提交 rows）
        if (StringUtils.hasText(row.getEmail()) && !row.getEmail().contains("@")) {
            throw new ValidationException("邮箱格式不正确: " + row.getEmail());
        }

        Customer existing = null;
        if (idempotency && StringUtils.hasText(row.getExternalBuyerId())) {
            existing = customerRepository
                    .findByExternalBuyerId(tenantId, platform, row.getExternalBuyerId()).orElse(null);
        }
        if (existing == null && StringUtils.hasText(row.getEmail())) {
            existing = customerRepository.findByEmail(row.getEmail()).orElse(null);
        }

        if (existing != null) {
            if (!updateExisting) {
                job.recordSkipped(CustomerImportError.of(rowNumber, row.getExternalBuyerId(),
                        row.getCustomerName(), CustomerImportTargetField.EXTERNAL_BUYER_ID.name(),
                        "平台买家已存在且未开启覆盖更新，已跳过"));
                return;
            }
            existing.mergeImportedProfile(row.getCustomerName(), row.getEmail(), row.getPhone(),
                    row.getFullAddress(), row.getCompany());
            Customer saved = customerRepository.save(existing);
            job.recordSuccess(saved.getCustomerId(), true);
        } else {
            Customer created = customerApplicationService.createCustomerFromPlatform(tenantId,
                    row.getCustomerName(), row.getEmail(), row.getPhone(), row.getFullAddress(),
                    row.getCompany(), platform, row.getExternalBuyerId());
            job.recordSuccess(created.getCustomerId(), false);
        }
    }

    private List<Map<String, Object>> resolveRawRows(CustomerImportPreviewRequest request, String tenantId,
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
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取客户；可勾选「使用示例数据」或改用文件上传导入");
        }
        boolean fromOrders = SOURCE_TYPE_ORDERS.equalsIgnoreCase(
                StringUtils.hasText(request.getSourceType()) ? request.getSourceType().trim() : SOURCE_TYPE_CUSTOMERS);
        if (fromOrders) {
            // 从平台订单数据中萃取买家：先按订单维度拉取，再按买家去重
            PlatformOrderFetchRequest fetchRequest = new PlatformOrderFetchRequest(
                    tenantId,
                    platform,
                    integration == null ? null : integration.getStoreUrl(),
                    integration == null ? null : integration.getApiKey(),
                    integration == null ? null : integration.getApiSecret(),
                    request.getOrderNumber(),
                    parseDateTime(request.getUpdatedFrom()),
                    parseDateTime(request.getUpdatedTo()),
                    request.getLimit() == null ? 50 : request.getLimit(),
                    request.getPage() == null ? 1 : request.getPage());
            PlatformCredential credential = platformCredentials.resolve(integration);
            PlatformOrderFetcher fetcher = fetcherRegistry.orderFetcher(credential, useSample);
            if (useSample) {
                warnings.add("当前使用平台示例数据，正式环境请关闭该选项以调用真实平台接口");
            }
            return extractBuyerRows(fetcher.fetch(fetchRequest, credential), warnings);
        }
        PlatformCustomerFetchRequest fetchRequest = new PlatformCustomerFetchRequest(
                tenantId,
                platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                request.getKeyword(),
                request.getLimit() == null ? 50 : request.getLimit(),
                request.getPage() == null ? 1 : request.getPage());
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformCustomerFetcher fetcher = fetcherRegistry.customerFetcher(credential, useSample);
        if (useSample) {
            warnings.add("当前使用平台示例数据，正式环境请关闭该选项以调用真实平台接口");
        }
        return fetcher.fetch(fetchRequest, credential);
    }

    /**
     * 从平台订单原始行中萃取买家：按「平台买家ID -> 邮箱 -> 买家名称」优先级分组去重，
     * 每组保留第一行原样返回（字段名保持订单结构不动，让既有的字段映射管线继续工作）。
     */
    private List<Map<String, Object>> extractBuyerRows(List<Map<String, Object>> orderRows, List<String> warnings) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        if (orderRows != null) {
            int index = 0;
            for (Map<String, Object> orderRow : orderRows) {
                index++;
                if (orderRow == null) {
                    continue;
                }
                String buyerKey = resolveBuyerGroupKey(orderRow, index);
                grouped.putIfAbsent(buyerKey, orderRow);
            }
        }
        int orderCount = orderRows == null ? 0 : orderRows.size();
        warnings.add("已从订单数据中按买家去重，" + orderCount + " 条订单行合并为 " + grouped.size() + " 个买家");
        return new ArrayList<>(grouped.values());
    }

    /**
     * 买家分组键：不硬编码字段名，用别名表在原始 key 集合中定位
     * EXTERNAL_BUYER_ID / CUSTOMER_EMAIL / CUSTOMER_NAME 对应列，依次兜底。
     */
    private String resolveBuyerGroupKey(Map<String, Object> orderRow, int index) {
        String value = firstValueByTarget(orderRow, CustomerImportTargetField.EXTERNAL_BUYER_ID);
        if (value == null) {
            value = firstValueByTarget(orderRow, CustomerImportTargetField.CUSTOMER_EMAIL);
        }
        if (value == null) {
            value = firstValueByTarget(orderRow, CustomerImportTargetField.CUSTOMER_NAME);
        }
        if (value == null) {
            // 无法识别买家身份时按行独立保留，交由后续映射产生行级错误
            return "row-" + index;
        }
        return value.toLowerCase();
    }

    private String firstValueByTarget(Map<String, Object> rawRow, CustomerImportTargetField target) {
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (CustomerImportTargetField.detectByAlias(entry.getKey()).orElse(null) == target) {
                String text = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
                if (text != null && !text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
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
        return TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID);
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

    private List<CustomerFieldMapping> toDomainMappings(List<FieldMappingDto> dtos, String tenantId,
                                                        String platform) {
        List<CustomerFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (FieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            CustomerImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = CustomerImportTargetField.valueOf(dto.getTargetField().trim());
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
            CustomerFieldMapping mapping = CustomerFieldMapping.create(tenantId, platform, dto.getSourceField().trim(),
                    targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<FieldMappingDto> toMappingDtos(List<CustomerFieldMapping> mappings) {
        List<FieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (CustomerFieldMapping mapping : mappings) {
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

    private CustomerImportRowDto toRowDto(ImportedCustomerRow row) {
        CustomerImportRowDto dto = new CustomerImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setExternalBuyerId(row.externalBuyerId());
        dto.setCustomerName(row.customerName());
        dto.setEmail(row.email());
        dto.setPhone(row.phone());
        dto.setCompany(row.company());
        dto.setFullAddress(row.fullAddress());
        return dto;
    }

    private List<CustomerImportErrorDto> toErrorDtos(List<CustomerImportError> errors) {
        List<CustomerImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (CustomerImportError error : errors) {
            CustomerImportErrorDto dto = new CustomerImportErrorDto();
            dto.setRowNumber(error.getRowNumber());
            dto.setExternalBuyerId(error.getExternalBuyerId());
            dto.setCustomerName(error.getCustomerName());
            dto.setField(error.getField());
            dto.setMessage(error.getMessage());
            dtos.add(dto);
        }
        return dtos;
    }

    private CustomerImportJobDto toJobDto(CustomerImportJob job, boolean includeIds) {
        CustomerImportJobDto dto = new CustomerImportJobDto();
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
            dto.setCreatedCustomerIds(job.getCreatedCustomerIds());
            dto.setUpdatedCustomerIds(job.getUpdatedCustomerIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(CustomerImportJob job) {
        customerImportJobRepository.save(job);
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
