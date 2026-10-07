package site.hexaarch.ecommerce.logistics.application.service.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.AddTraceRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipItemDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物流应用服务，协调领域对象完成物流相关的业务操作。
 * <p>
 * 对外统一输出 {@link LogisticsOrderDto}，避免把带领域事件的物流单聚合直接序列化给前端。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class LogisticsApplicationService {

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LogisticsOrderRepository logisticsOrderRepository;
    private final LogisticsChannelRepository logisticsChannelRepository;
    private final OrderRepository orderRepository;
    private final LogisticsTrackingService logisticsTrackingService;
    private final DomainEventPublisher domainEventPublisher;
    private final LogisticsOrderResolver logisticsOrderResolver;

    public LogisticsApplicationService(LogisticsOrderRepository logisticsOrderRepository,
                                       LogisticsChannelRepository logisticsChannelRepository,
                                       OrderRepository orderRepository,
                                       LogisticsTrackingService logisticsTrackingService,
                                       DomainEventPublisher domainEventPublisher,
                                       LogisticsOrderResolver logisticsOrderResolver) {
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.logisticsChannelRepository = logisticsChannelRepository;
        this.orderRepository = orderRepository;
        this.logisticsTrackingService = logisticsTrackingService;
        this.domainEventPublisher = domainEventPublisher;
        this.logisticsOrderResolver = logisticsOrderResolver;
    }

    /**
     * 创建物流单（HTTP 入口，订单与渠道均可按ID或编号指定）
     */
    @Transactional
    public LogisticsOrderDto createLogisticsOrder(CreateLogisticsRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        Order order = logisticsOrderResolver.resolveOrder(tenantId, request.getOrderId(), request.getOrderNumber(),
                null, null);
        LogisticsChannel channel = logisticsOrderResolver.resolveChannel(request.getLogisticsChannelId(),
                request.getChannelCode());

        LogisticsOrder logisticsOrder = LogisticsOrder.create(tenantId, order.getId(), channel.getChannelId(),
                request.getTrackingNumber(), StringUtils.hasText(request.getCarrier())
                        ? request.getCarrier() : channel.getChannelName());
        if (Boolean.TRUE.equals(request.getShipImmediately())) {
            logisticsOrder.markShipped(null);
        }

        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 创建物流单（消息与流程编排链路使用，渠道标识可为渠道ID或渠道编码）
     */
    @Transactional
    public LogisticsOrder createLogisticsOrder(String tenantId, String orderId, String channelIdentifier) {
        LogisticsChannel channel = logisticsOrderResolver.resolveChannelByIdOrCode(channelIdentifier);
        LogisticsOrder logisticsOrder = LogisticsOrder.create(tenantId, orderId, channel.getChannelId());
        LogisticsOrder savedLogisticsOrder = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(savedLogisticsOrder);
        return savedLogisticsOrder;
    }

    /**
     * 更新物流状态
     */
    @Transactional
    public LogisticsOrderDto updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus) {
        return updateLogisticsStatus(logisticsOrderId, newStatus, null);
    }

    /**
     * 更新物流状态（可附带轨迹描述）
     */
    @Transactional
    public LogisticsOrderDto updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus,
                                                   String description) {
        LogisticsOrder saved = persistStatusChange(getLogisticsOrder(logisticsOrderId), newStatus, description);
        return toDto(saved);
    }

    /**
     * 批量发货：逐单生成物流单并推进订单状态，单条失败不影响其他订单
     */
    public BatchShipResultDto batchShip(BatchShipRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        boolean syncOrderStatus = request.getUpdateOrderStatus() == null || request.getUpdateOrderStatus();
        List<BatchShipItemDto> items = resolveItems(request);
        BatchShipResultDto result = BatchShipResultDto.of(items.size());

        for (int i = 0; i < items.size(); i++) {
            BatchShipItemDto item = items.get(i);
            int rowNumber = item.getRowNumber() == null ? i + 1 : item.getRowNumber();
            String orderLabel = firstText(item.getOrderNumber(), item.getOrderId(), item.getExternalOrderId());
            try {
                shipSingle(tenantId, request, item, result, syncOrderStatus);
            } catch (Exception e) {
                log.warn("[BatchShip] 第{}行发货失败 order={}: {}", rowNumber, orderLabel, e.getMessage());
                result.recordFailure(rowNumber, orderLabel, normalizeErrorMessage(e));
            }
        }
        result.buildMessage();
        return result;
    }

    /**
     * 登记物流轨迹
     */
    @Transactional
    public LogisticsOrderDto addTrace(String logisticsOrderId, AddTraceRequest request) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        String description = request == null ? null : request.getDescription();
        if (!StringUtils.hasText(description)) {
            throw new ValidationException("轨迹描述不能为空");
        }
        LocalDateTime time = parseDateTime(request.getTime(), false);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim().toUpperCase() : null;
        logisticsOrder.appendTrace(new LogisticsTrace(time == null ? LocalDateTime.now() : time, status, description,
                request.getLocation()));
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 生成物流标签（面单）
     */
    @Transactional
    public LogisticsOrderDto generateLabel(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        if (logisticsOrder.getLogisticsLabel() != null) {
            return toDto(logisticsOrder);
        }
        Order order = orderRepository.findById(logisticsOrder.getOrderId()).orElse(null);
        LogisticsLabel label = LogisticsLabel.builder()
                .trackingNumber(logisticsOrder.getTrackingNumber())
                .recipientInfo(buildRecipientInfo(order))
                .packageInfo(LogisticsLabel.PackageInfo.builder().build())
                .labelFormat("PDF")
                .build();
        logisticsOrder.generateLabel(label);
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 标记发货（揽收）
     */
    @Transactional
    public LogisticsOrderDto ship(String logisticsOrderId, String shippedAt, String trackingNumber) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        logisticsOrder.assignTrackingNumber(trackingNumber);
        LocalDateTime shipped = parseDateTime(shippedAt, false);
        logisticsOrder.markShipped(shipped == null ? LocalDateTime.now() : shipped);
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 取消物流单（已被揽收/运输/送达的物流单不可取消）
     */
    @Transactional
    public LogisticsOrderDto cancel(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        LogisticsStatus current = logisticsOrder.getLogisticsStatus();
        if (current != null && (current.isCollected() || current.isInTransit() || current.isDelivered())) {
            throw new ValidationException("物流单已[" + current.getDescription() + "]，无法取消");
        }
        logisticsOrder.updateStatus(LogisticsStatus.CANCELLED, "手工取消");
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 刷新物流跟踪状态。
     * <p>
     * 当前未接入物流商实时查询接口，此处仅记录一次「查询」轨迹并回传现有状态；
     * 平台侧的物流状态推进由「物流导入」链路负责。
     * </p>
     */
    public LogisticsOrderDto track(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        LogisticsStatus latest = logisticsTrackingService.trackLogisticsStatus(logisticsOrderId);
        logisticsOrder.appendTrace(LogisticsTrace.of(LocalDateTime.now(),
                latest == null ? null : latest.name(), "已查询物流跟踪信息", null));
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 查询物流单详情
     */
    public LogisticsOrderDto getLogisticsOrderDto(String logisticsOrderId) {
        return toDto(getLogisticsOrder(logisticsOrderId));
    }

    /**
     * 查找物流单
     */
    public LogisticsOrder getLogisticsOrder(String logisticsOrderId) {
        return logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new EntityNotFoundException("物流单不存在: " + logisticsOrderId));
    }

    /**
     * 根据订单ID查找物流单
     */
    public List<LogisticsOrderDto> findLogisticsOrderByOrderId(String orderId) {
        List<LogisticsOrder> orders = logisticsOrderRepository.findByOrderId(orderId);
        List<LogisticsOrderDto> dtos = new ArrayList<>(orders.size());
        for (LogisticsOrder order : orders) {
            dtos.add(toDto(order));
        }
        return dtos;
    }

    /**
     * 条件分页查询物流单
     */
    public PageResult<LogisticsOrderDto> search(String tenantId, String trackingNumber, String orderId,
                                                String channelId, String status, String keyword, String startDate,
                                                String endDate, int page, int size) {
        LogisticsStatus logisticsStatus = null;
        if (StringUtils.hasText(status)) {
            try {
                logisticsStatus = LogisticsStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("不支持的物流状态: " + status);
            }
        }
        String resolvedChannelId = StringUtils.hasText(channelId)
                ? logisticsOrderResolver.resolveChannelByIdOrCode(channelId).getChannelId() : null;
        return logisticsOrderRepository
                .search(resolveTenant(tenantId), trackingNumber, orderId, resolvedChannelId, logisticsStatus,
                        keyword, startDate, endDate, Math.max(page, 0), Math.min(Math.max(size, 1), 200))
                .map(this::toDto);
    }

    /**
     * 查找所有物流单
     */
    public List<LogisticsOrderDto> findAllLogisticsOrders() {
        List<LogisticsOrder> orders = logisticsOrderRepository.findAll();
        List<LogisticsOrderDto> dtos = new ArrayList<>(orders.size());
        for (LogisticsOrder order : orders) {
            dtos.add(toDto(order));
        }
        return dtos;
    }

    /**
     * 删除物流单
     */
    @Transactional
    public void delete(String logisticsOrderId) {
        getLogisticsOrder(logisticsOrderId);
        logisticsOrderRepository.delete(logisticsOrderId);
    }

    private void shipSingle(String tenantId, BatchShipRequest request, BatchShipItemDto item,
                            BatchShipResultDto result, boolean syncOrderStatus) {
        Order order = logisticsOrderResolver.resolveOrder(tenantId, item.getOrderId(), item.getOrderNumber(),
                item.getExternalOrderId(), null);
        LogisticsChannel channel = logisticsOrderResolver.resolveChannel(
                firstText(item.getLogisticsChannelId(), request.getLogisticsChannelId()),
                firstText(item.getChannelCode(), request.getChannelCode()));

        LogisticsOrder pending = null;
        for (LogisticsOrder existing : logisticsOrderRepository.findByOrderId(order.getId())) {
            LogisticsStatus status = existing.getLogisticsStatus();
            if (status != null && status != LogisticsStatus.PENDING) {
                result.recordSkipped(order.getOrderNumber(),
                        "订单已存在物流单[" + existing.getTrackingNumber() + "]，状态为[" + status.getDescription()
                                + "]，已跳过");
                return;
            }
            pending = existing;
        }

        LocalDateTime shippedAt = parseDateTime(item.getShippedAt(), false);
        LogisticsOrder logisticsOrder = pending != null
                ? pending
                : LogisticsOrder.create(tenantId, order.getId(), channel.getChannelId(), item.getTrackingNumber(),
                firstText(item.getCarrier(), channel.getChannelName()));
        logisticsOrder.assignTrackingNumber(item.getTrackingNumber());
        if (syncOrderStatus && order.getOrderStatus() != OrderStatus.SHIPPED
                && order.getOrderStatus() != OrderStatus.COMPLETED) {
            // 订单状态推进放在持久化之前，状态机不允许时整行回滚为「未处理」
            order.updateStatus(OrderStatus.SHIPPED);
        }
        logisticsOrder.markShipped(shippedAt == null ? LocalDateTime.now() : shippedAt);

        if (syncOrderStatus) {
            Order savedOrder = orderRepository.save(order);
            domainEventPublisher.publishEventsFrom(savedOrder);
        }
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        result.recordSuccess(saved.getLogisticsOrderId());
    }

    private LogisticsOrder persistStatusChange(LogisticsOrder logisticsOrder, LogisticsStatus newStatus,
                                               String description) {
        if (newStatus == null) {
            throw new ValidationException("物流状态不能为空");
        }
        logisticsOrder.updateStatus(newStatus, description);
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return saved;
    }

    private List<BatchShipItemDto> resolveItems(BatchShipRequest request) {
        List<BatchShipItemDto> items = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            items.addAll(request.getItems());
            return items;
        }
        if (request.getOrderIds() != null) {
            for (String orderId : request.getOrderIds()) {
                if (!StringUtils.hasText(orderId)) {
                    continue;
                }
                BatchShipItemDto item = new BatchShipItemDto();
                item.setOrderId(orderId.trim());
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new ValidationException("批量发货明细为空，请提供 items 或 orderIds");
        }
        return items;
    }

    private LogisticsLabel.RecipientInfo buildRecipientInfo(Order order) {
        if (order == null) {
            return null;
        }
        ShippingAddress address = order.getShippingAddress();
        if (address == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : List.of(address.getCountry(), address.getProvince(), address.getCity(),
                address.getStreet())) {
            if (StringUtils.hasText(part)) {
                parts.add(part);
            }
        }
        return LogisticsLabel.RecipientInfo.builder()
                .name(address.getRecipient())
                .phone(address.getPhone())
                .address(String.join(" ", parts))
                .build();
    }

    private LogisticsOrderDto toDto(LogisticsOrder logisticsOrder) {
        LogisticsOrderDto dto = LogisticsOrderDto.from(logisticsOrder);
        dto.setCreatedAt(format(logisticsOrder.getCreatedAt()));
        dto.setUpdatedAt(format(logisticsOrder.getUpdatedAt()));
        dto.setShippedAt(format(logisticsOrder.getShippedAt()));
        dto.setEstimatedArrivalAt(format(logisticsOrder.getEstimatedArrivalAt()));
        dto.setDeliveredAt(format(logisticsOrder.getDeliveredAt()));

        if (StringUtils.hasText(logisticsOrder.getOrderId())) {
            orderRepository.findById(logisticsOrder.getOrderId())
                    .ifPresent(order -> dto.setOrderNumber(order.getOrderNumber()));
        }
        if (StringUtils.hasText(logisticsOrder.getLogisticsChannelId())) {
            logisticsChannelRepository.findById(logisticsOrder.getLogisticsChannelId())
                    .ifPresent(channel -> {
                        dto.setChannelCode(channel.getChannelCode());
                        dto.setChannelName(channel.getChannelName());
                    });
        }
        return dto;
    }

    private String resolveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : LogisticsImportApplicationService.DEFAULT_TENANT_ID;
    }

    /**
     * 兼容 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd 两种入参格式
     *
     * @param required 为 true 时格式非法直接报错，为 false 时静默返回 null
     */
    private LocalDateTime parseDateTime(String text, boolean required) {
        if (!StringUtils.hasText(text)) {
            if (required) {
                throw new ValidationException("时间不能为空（格式 yyyy-MM-dd HH:mm:ss）");
            }
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDateTime.parse(trimmed, INPUT_FORMATTER);
        } catch (Exception ignored) {
            // 继续尝试仅日期格式
        }
        try {
            return java.time.LocalDate.parse(trimmed, DATE_ONLY_FORMATTER).atStartOfDay();
        } catch (Exception ignored) {
            if (required) {
                throw new ValidationException("时间格式不正确（应为 yyyy-MM-dd HH:mm:ss）: " + text);
            }
            return null;
        }
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(INPUT_FORMATTER);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
