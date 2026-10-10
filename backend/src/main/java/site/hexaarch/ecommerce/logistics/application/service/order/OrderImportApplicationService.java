package site.hexaarch.ecommerce.logistics.application.service.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.OrderImportJob;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderImportError;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.service.OrderFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderRow;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportItemDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportRowDto;
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
 * 第三方电商平台订单导入应用服务。
 * <p>
 * 完整链路：订单数据获取 -> 字段映射 -> 批量导入 -> 行级异常处理 -> 导入结果反馈（任务记录）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class OrderImportApplicationService {

    public static final String FILE_PLATFORM = "FILE";
    public static final String DEFAULT_TENANT_ID = "default-tenant";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderRepository orderRepository;
    private final OrderFieldMappingService orderFieldMappingService;
    private final OrderFieldMappingRepository orderFieldMappingRepository;
    private final OrderImportJobRepository orderImportJobRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final ImportSourceParser importSourceParser;
    private final DomainEventPublisher domainEventPublisher;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public OrderImportApplicationService(OrderRepository orderRepository,
                                         OrderFieldMappingService orderFieldMappingService,
                                         OrderFieldMappingRepository orderFieldMappingRepository,
                                         OrderImportJobRepository orderImportJobRepository,
                                         PlatformIntegrationRepository platformIntegrationRepository,
                                         ImportSourceParser importSourceParser,
                                         DomainEventPublisher domainEventPublisher,
                                         PlatformFetcherRegistry fetcherRegistry,
                                         PlatformCredentials platformCredentials) {
        this.orderRepository = orderRepository;
        this.orderFieldMappingService = orderFieldMappingService;
        this.orderFieldMappingRepository = orderFieldMappingRepository;
        this.orderImportJobRepository = orderImportJobRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.importSourceParser = importSourceParser;
        this.domainEventPublisher = domainEventPublisher;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 预览：拉取/解析原始数据并完成字段映射，不写入订单库
     */
    public OrderImportPreviewResponse preview(OrderImportPreviewRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawRows = resolveRawRows(request, tenantId, platform, integration, warnings);
        List<String> sourceFields = orderFieldMappingService.detectSourceFields(rawRows);

        List<OrderFieldMapping> mappings = request.getMappings() != null && !request.getMappings().isEmpty()
                ? toDomainMappings(request.getMappings(), tenantId, platform)
                : orderFieldMappingService.resolveMappings(tenantId, platform, sourceFields);

        List<OrderImportRowDto> rows = new ArrayList<>(rawRows.size());
        List<OrderImportErrorDto> rowErrors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            var result = orderFieldMappingService.mapRow(mappings, rawRows.get(i), i + 1);
            OrderImportRowDto dto = toRowDto(result.row());
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

        OrderImportPreviewResponse response = new OrderImportPreviewResponse();
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
    public OrderImportJobDto importOrders(OrderImportRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        PlatformIntegration integration = resolveIntegration(request.getIntegrationId());
        String platform = resolvePlatform(request.getPlatform(), integration);
        boolean updateExisting = request.getUpdateExisting() == null || request.getUpdateExisting();
        boolean idempotency = request.getEnableIdempotency() == null || request.getEnableIdempotency();

        List<OrderImportRowDto> rows = request.getRows();
        String fileName = request.getFileName();
        if (rows == null || rows.isEmpty()) {
            // 未携带预览结果时，重新走「获取 + 映射」链路
            OrderImportPreviewRequest previewRequest = new OrderImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setIntegrationId(request.getIntegrationId());
            previewRequest.setPlatform(platform);
            previewRequest.setOrderNumber(request.getOrderNumber());
            previewRequest.setUpdatedFrom(request.getUpdatedFrom());
            previewRequest.setUpdatedTo(request.getUpdatedTo());
            previewRequest.setLimit(request.getLimit());
            previewRequest.setPage(request.getPage());
            previewRequest.setUseSampleData(request.getUseSampleData());
            previewRequest.setMappings(request.getMappings());
            OrderImportPreviewResponse previewed = preview(previewRequest);
            rows = previewed.getRows();
            platform = previewed.getPlatform();
        }

        OrderImportJob job = OrderImportJob.create(tenantId, platform,
                integration == null ? null : String.valueOf(integration.getId()), fileName);
        job.start(rows == null ? 0 : rows.size());

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                OrderImportRowDto row = rows.get(i);
                int rowNumber = row.getRowNumber() == null ? i + 1 : row.getRowNumber();
                try {
                    processRow(row, rowNumber, tenantId, platform, updateExisting, idempotency, job);
                } catch (Exception e) {
                    log.warn("[OrderImport] 第{}行导入失败: {}", rowNumber, e.getMessage());
                    job.recordFailure(OrderImportError.of(rowNumber, row.getExternalOrderId(),
                            row.getOrderNumber(), null, normalizeErrorMessage(e)));
                }
            }
        }
        job.complete();
        persistJob(job);
        return toJobDto(job, true);
    }

    /**
     * 从指定平台集成同步订单（供「平台集成-同步订单」入口调用）。
     * 直接调用本类方法时不经过事务代理，因此与 {@link #importOrders} 同样声明 {@code @Transactional}。
     */
    @Transactional
    public OrderImportJobDto syncFromPlatform(String integrationId, String tenantId, String orderNumber,
                                              String updatedFrom, String updatedTo, Integer limit,
                                              Boolean useSampleData) {
        OrderImportRequest request = new OrderImportRequest();
        request.setIntegrationId(integrationId);
        request.setTenantId(tenantId);
        request.setOrderNumber(orderNumber);
        request.setUpdatedFrom(updatedFrom);
        request.setUpdatedTo(updatedTo);
        request.setLimit(limit);
        request.setUseSampleData(useSampleData);
        request.setUpdateExisting(Boolean.TRUE);
        request.setEnableIdempotency(Boolean.TRUE);
        return importOrders(request);
    }

    /**
     * 查询导入任务详情
     */
    public OrderImportJobDto getJob(String jobId) {
        return orderImportJobRepository.findById(jobId)
                .map(job -> toJobDto(job, false))
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + jobId));
    }

    /**
     * 分页查询导入任务
     */
    public PageResult<OrderImportJobDto> listJobs(String tenantId, int page, int size) {
        PageResult<OrderImportJob> jobs = orderImportJobRepository.findByTenantId(tenantId, page, size);
        return jobs.map(job -> toJobDto(job, true));
    }

    /**
     * 查询指定平台生效的字段映射
     */
    public List<FieldMappingDto> getMappings(String tenantId, String platform) {
        if (!StringUtils.hasText(platform)) {
            return List.of();
        }
        return toMappingDtos(orderFieldMappingRepository
                .findByTenantIdAndPlatform(resolveTenant(tenantId), platform));
    }

    /**
     * 保存字段映射（按租户+平台整体替换）
     */
    @Transactional
    public List<FieldMappingDto> saveMappings(FieldMappingSaveRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        String platform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : FILE_PLATFORM;
        List<OrderFieldMapping> mappings = toDomainMappings(request.getMappings(), tenantId, platform);
        for (OrderFieldMapping mapping : mappings) {
            if (mapping.getTargetField() == null) {
                throw new ValidationException("字段映射缺少目标字段: " + mapping.getSourceField());
            }
        }
        orderFieldMappingRepository.deleteByTenantIdAndPlatform(tenantId, platform);
        List<OrderFieldMapping> saved = orderFieldMappingRepository.saveAll(mappings);
        return toMappingDtos(saved);
    }

    /**
     * 标准目标字段列表（含中文说明与是否必填）
     */
    public List<TargetFieldDto> targetFields() {
        List<TargetFieldDto> fields = new ArrayList<>();
        for (OrderImportTargetField field : OrderImportTargetField.values()) {
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
     * 订单状态字典，供导入时状态映射与前端下拉使用
     */
    public List<TargetFieldDto> orderStatuses() {
        List<TargetFieldDto> statuses = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            statuses.add(new TargetFieldDto(status.name(), status.getLabel(), Boolean.FALSE));
        }
        return statuses;
    }

    /**
     * 生成标准CSV导入模板
     */
    public String csvTemplate() {
        return String.join(",", List.of("externalOrderId", "orderNumber", "customerName", "customerEmail",
                "orderStatus", "orderTime", "paymentMethod", "paymentStatus", "transactionId", "totalAmount",
                "currency", "recipientName", "phone", "country", "province", "city", "street", "zipCode",
                "sku", "productName", "quantity", "unitPrice")) + "\n"
                + "AMAZON-ORD-001,,张三,zhangsan@example.com,Pending,2026-09-01 10:00:00,CREDIT_CARD,PAID,"
                + "TXN-001,199.00,CNY,张三,13800138000,中国,广东省,深圳市,南山区科技园路1号,518000,"
                + "SKU-001,示例商品,2,99.50\n";
    }

    private void processRow(OrderImportRowDto row, int rowNumber, String tenantId, String platform,
                            boolean updateExisting, boolean idempotency, OrderImportJob job) {
        if (row.getErrors() != null && !row.getErrors().isEmpty()) {
            OrderImportErrorDto first = row.getErrors().get(0);
            throw new ValidationException(first.getMessage());
        }
        String externalOrderId = row.getExternalOrderId();
        if (!StringUtils.hasText(externalOrderId)) {
            throw new ValidationException("平台订单号为空，无法建立幂等关联");
        }

        List<OrderItem> items = toOrderItems(row);
        if (items.isEmpty()) {
            throw new ValidationException("订单明细为空");
        }
        BigDecimal total = row.getTotalAmount() != null && row.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                ? row.getTotalAmount()
                : items.stream().map(OrderItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        ShippingAddress address = ShippingAddress.builder()
                .country(nvl(row.getCountry()))
                .province(nvl(row.getProvince()))
                .city(nvl(row.getCity()))
                .street(nvl(row.getStreet()))
                .zipCode(nvl(row.getZipCode()))
                .recipient(nvl(row.getRecipient()))
                .phone(nvl(row.getPhone()))
                .build();
        PaymentInfo payment = PaymentInfo.builder()
                .paymentMethod(StringUtils.hasText(row.getPaymentMethod()) ? row.getPaymentMethod() : "UNKNOWN")
                .paymentStatus(StringUtils.hasText(row.getPaymentStatus()) ? row.getPaymentStatus() : "PENDING")
                .transactionId(StringUtils.hasText(row.getTransactionId()) ? row.getTransactionId() : "N/A")
                .amount(total)
                .build();
        OrderStatus status = OrderStatus.fromCode(row.getStatus()).orElse(OrderStatus.PENDING);
        String customerId = StringUtils.hasText(row.getCustomerId()) ? row.getCustomerId() : "";

        boolean isUpdate = false;
        Order existing = idempotency
                ? orderRepository.findByPlatformOrderId(tenantId, platform, externalOrderId).orElse(null)
                : null;

        Order target;
        if (existing != null) {
            if (!updateExisting) {
                job.recordSkipped(OrderImportError.of(rowNumber, externalOrderId, row.getOrderNumber(),
                        OrderImportTargetField.EXTERNAL_ORDER_ID.name(),
                        "平台订单号已存在且未开启覆盖更新，已跳过"));
                return;
            }
            isUpdate = true;
            target = Order.builder()
                    .id(existing.getId())
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .customerName(row.getCustomerName())
                    .customerEmail(row.getCustomerEmail())
                    .orderNumber(StringUtils.hasText(row.getOrderNumber())
                            ? row.getOrderNumber() : existing.getOrderNumber())
                    .orderItems(items)
                    .shippingAddress(address)
                    .paymentInfo(payment)
                    .createdAt(existing.getCreatedAt())
                    .orderStatus(status)
                    .updatedAt(LocalDateTime.now())
                    .platform(platform)
                    .platformOrderId(externalOrderId)
                    .build();
        } else {
            target = Order.createFromPlatform(tenantId, platform, externalOrderId, row.getOrderNumber(),
                    customerId, row.getCustomerName(), row.getCustomerEmail(), items, address, payment,
                    status, row.getOrderedAt());
        }

        Order saved = orderRepository.save(target);
        domainEventPublisher.publishEventsFrom(saved);
        job.recordSuccess(saved.getId(), isUpdate);
    }

    private List<OrderItem> toOrderItems(OrderImportRowDto row) {
        List<OrderItem> items = new ArrayList<>();
        if (row.getItems() == null) {
            return items;
        }
        for (OrderImportItemDto item : row.getItems()) {
            if (item == null) {
                continue;
            }
            String sku = StringUtils.hasText(item.getSku()) ? item.getSku().trim() : "UNKNOWN-SKU";
            String name = StringUtils.hasText(item.getName()) ? item.getName().trim() : sku;
            int quantity = item.getQuantity() == null || item.getQuantity() <= 0 ? 1 : item.getQuantity();
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            items.add(OrderItem.create(sku, sku, name, quantity, unitPrice));
        }
        return items;
    }

    private List<Map<String, Object>> resolveRawRows(OrderImportPreviewRequest request, String tenantId,
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
            throw new ValidationException("尚未选择平台集成(integrationId)，无法拉取订单；可勾选「使用示例数据」或改用文件上传导入");
        }
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

    private List<OrderFieldMapping> toDomainMappings(List<FieldMappingDto> dtos, String tenantId, String platform) {
        List<OrderFieldMapping> mappings = new ArrayList<>();
        if (dtos == null) {
            return mappings;
        }
        for (FieldMappingDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.getSourceField())) {
                continue;
            }
            OrderImportTargetField targetField = null;
            if (StringUtils.hasText(dto.getTargetField())) {
                try {
                    targetField = OrderImportTargetField.valueOf(dto.getTargetField().trim());
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
            OrderFieldMapping mapping = OrderFieldMapping.create(tenantId, platform, dto.getSourceField().trim(),
                    targetField, transform, dto.getDefaultValue());
            mapping.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            mappings.add(mapping);
        }
        return mappings;
    }

    private List<FieldMappingDto> toMappingDtos(List<OrderFieldMapping> mappings) {
        List<FieldMappingDto> dtos = new ArrayList<>();
        if (mappings == null) {
            return dtos;
        }
        for (OrderFieldMapping mapping : mappings) {
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

    private OrderImportRowDto toRowDto(ImportedOrderRow row) {
        OrderImportRowDto dto = new OrderImportRowDto();
        dto.setRowNumber(row.rowNumber());
        dto.setExternalOrderId(row.externalOrderId());
        dto.setOrderNumber(row.orderNumber());
        dto.setCustomerId(row.customerId());
        dto.setCustomerName(row.customerName());
        dto.setCustomerEmail(row.customerEmail());
        dto.setStatus(row.status() == null ? OrderStatus.PENDING.name() : row.status().name());
        dto.setCurrency(row.currency());
        dto.setOrderedAt(row.orderedAt());
        dto.setPaymentMethod(row.paymentMethod());
        dto.setPaymentStatus(row.paymentStatus());
        dto.setTransactionId(row.transactionId());
        dto.setTotalAmount(row.effectiveTotal());
        dto.setRecipient(row.recipient());
        dto.setPhone(row.phone());
        dto.setCountry(row.country());
        dto.setProvince(row.province());
        dto.setCity(row.city());
        dto.setStreet(row.street());
        dto.setZipCode(row.zipCode());
        List<OrderImportItemDto> items = new ArrayList<>();
        if (row.items() != null) {
            for (ImportedOrderItem item : row.items()) {
                OrderImportItemDto itemDto = new OrderImportItemDto();
                itemDto.setSku(item.sku());
                itemDto.setName(item.name());
                itemDto.setQuantity(item.quantity());
                itemDto.setUnitPrice(item.unitPrice());
                itemDto.setTotalPrice(item.totalPrice());
                items.add(itemDto);
            }
        }
        dto.setItems(items);
        return dto;
    }

    private List<OrderImportErrorDto> toErrorDtos(List<OrderImportError> errors) {
        List<OrderImportErrorDto> dtos = new ArrayList<>();
        if (errors == null) {
            return dtos;
        }
        for (OrderImportError error : errors) {
            OrderImportErrorDto dto = new OrderImportErrorDto();
            dto.setRowNumber(error.getRowNumber());
            dto.setExternalOrderId(error.getExternalOrderId());
            dto.setOrderNumber(error.getOrderNumber());
            dto.setField(error.getField());
            dto.setMessage(error.getMessage());
            dtos.add(dto);
        }
        return dtos;
    }

    private OrderImportJobDto toJobDto(OrderImportJob job, boolean includeIds) {
        OrderImportJobDto dto = new OrderImportJobDto();
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
            dto.setCreatedOrderIds(job.getCreatedOrderIds());
            dto.setUpdatedOrderIds(job.getUpdatedOrderIds());
        }
        return dto;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private void persistJob(OrderImportJob job) {
        orderImportJobRepository.save(job);
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
