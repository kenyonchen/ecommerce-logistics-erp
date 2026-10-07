package site.hexaarch.ecommerce.logistics.domain.logistics.aggregate;

import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsLabelGeneratedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsOrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 物流单聚合根，是物流单聚合的核心实体，负责管理物流状态、物流标签、物流费用、运单轨迹等。
 *
 * @author kenyon
 */
public class LogisticsOrder {

    /**
     * 允许直接流转到目标状态的来源状态集合，终态（已送达/已取消）不再允许变更
     */
    private static final java.util.Set<LogisticsStatus> FINAL_STATUSES =
            java.util.Set.of(LogisticsStatus.DELIVERED, LogisticsStatus.CANCELLED);

    private final List<Object> domainEvents = new ArrayList<>();

    private String logisticsOrderId;
    private String logisticsOrderNumber;
    private String tenantId;
    private String orderId;
    private String logisticsChannelId;
    private String carrier;
    private String trackingNumber;
    private String trackingUrl;
    private String platform;
    private String externalLogisticsId;
    private LogisticsStatus logisticsStatus;
    private LogisticsLabel logisticsLabel;
    private LogisticsFee logisticsFee;
    private LocalDateTime shippedAt;
    private LocalDateTime estimatedArrivalAt;
    private LocalDateTime deliveredAt;
    private final List<LogisticsTrace> traces = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 受保护的无参构造函数，供持久化框架使用。
     */
    protected LogisticsOrder() {
    }

    private LogisticsOrder(Builder builder) {
        this.logisticsOrderId = builder.logisticsOrderId != null ? builder.logisticsOrderId
                : java.util.UUID.randomUUID().toString();
        this.logisticsOrderNumber = builder.logisticsOrderNumber != null ? builder.logisticsOrderNumber
                : generateLogisticsOrderNumber();
        this.tenantId = builder.tenantId;
        this.orderId = builder.orderId;
        this.logisticsChannelId = builder.logisticsChannelId;
        this.carrier = builder.carrier;
        this.trackingNumber = builder.trackingNumber != null ? builder.trackingNumber : generateTrackingNumber();
        this.trackingUrl = builder.trackingUrl;
        this.platform = builder.platform;
        this.externalLogisticsId = builder.externalLogisticsId;
        this.logisticsStatus = builder.logisticsStatus != null ? builder.logisticsStatus : LogisticsStatus.PENDING;
        this.logisticsLabel = builder.logisticsLabel;
        this.logisticsFee = builder.logisticsFee;
        this.shippedAt = builder.shippedAt;
        this.estimatedArrivalAt = builder.estimatedArrivalAt;
        this.deliveredAt = builder.deliveredAt;
        if (builder.traces != null) {
            this.traces.addAll(builder.traces);
        }
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : LocalDateTime.now();
    }

    /**
     * 创建新的物流单。
     *
     * @param tenantId           租户ID
     * @param orderId            订单ID
     * @param logisticsChannelId 物流渠道ID
     * @return 新的物流单
     */
    public static LogisticsOrder create(String tenantId, String orderId, String logisticsChannelId) {
        return create(tenantId, orderId, logisticsChannelId, null, null);
    }

    /**
     * 创建新的物流单（可指定运单号与承运商，用于批量发货场景）。
     *
     * @param tenantId           租户ID
     * @param orderId            订单ID
     * @param logisticsChannelId 物流渠道ID
     * @param trackingNumber     运单号，为空时系统生成
     * @param carrier            承运商名称
     * @return 新的物流单
     */
    public static LogisticsOrder create(String tenantId, String orderId, String logisticsChannelId,
                                        String trackingNumber, String carrier) {
        Objects.requireNonNull(orderId, "订单ID不能为空");
        Objects.requireNonNull(logisticsChannelId, "物流渠道ID不能为空");

        LogisticsOrder logisticsOrder = builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .logisticsChannelId(logisticsChannelId)
                .trackingNumber(trackingNumber)
                .carrier(carrier)
                .logisticsStatus(LogisticsStatus.PENDING)
                .build();

        logisticsOrder.registerDomainEvent(LogisticsOrderCreatedEvent.builder()
                .logisticsOrderId(logisticsOrder.getLogisticsOrderId())
                .tenantId(logisticsOrder.getTenantId())
                .orderId(logisticsOrder.getOrderId())
                .logisticsChannelId(logisticsOrder.getLogisticsChannelId())
                .trackingNumber(logisticsOrder.getTrackingNumber())
                .createdAt(logisticsOrder.getCreatedAt())
                .build());
        return logisticsOrder;
    }

    /**
     * 依据持久化结果还原物流单实例，避免通过反射写入字段。
     */
    public static LogisticsOrder restore(String logisticsOrderId, String logisticsOrderNumber, String tenantId,
                                         String orderId, String logisticsChannelId, String carrier,
                                         String trackingNumber, String trackingUrl, String platform,
                                         String externalLogisticsId, LogisticsStatus logisticsStatus,
                                         LogisticsLabel logisticsLabel, LogisticsFee logisticsFee,
                                         LocalDateTime shippedAt, LocalDateTime estimatedArrivalAt,
                                         LocalDateTime deliveredAt, List<LogisticsTrace> traces,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        return builder()
                .logisticsOrderId(logisticsOrderId)
                .logisticsOrderNumber(logisticsOrderNumber)
                .tenantId(tenantId)
                .orderId(orderId)
                .logisticsChannelId(logisticsChannelId)
                .carrier(carrier)
                .trackingNumber(trackingNumber)
                .trackingUrl(trackingUrl)
                .platform(platform)
                .externalLogisticsId(externalLogisticsId)
                .logisticsStatus(logisticsStatus)
                .logisticsLabel(logisticsLabel)
                .logisticsFee(logisticsFee)
                .shippedAt(shippedAt)
                .estimatedArrivalAt(estimatedArrivalAt)
                .deliveredAt(deliveredAt)
                .traces(traces)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 生成物流单号。
     */
    private static String generateLogisticsOrderNumber() {
        return "LG" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 生成运单号。真实场景应由物流渠道API返回，此处作为兜底。
     */
    private static String generateTrackingNumber() {
        return "TN" + System.currentTimeMillis() + java.util.UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();
    }

    /**
     * 更新物流状态，并追加轨迹节点。
     *
     * @param newStatus 新的物流状态
     */
    public void updateStatus(LogisticsStatus newStatus) {
        updateStatus(newStatus, null);
    }

    /**
     * 更新物流状态。
     *
     * @param newStatus   新的物流状态
     * @param description 轨迹描述，为空时使用状态中文名
     */
    public void updateStatus(LogisticsStatus newStatus, String description) {
        if (newStatus == null) {
            throw new IllegalArgumentException("物流状态不能为空");
        }
        if (this.logisticsStatus != null && FINAL_STATUSES.contains(this.logisticsStatus)
                && this.logisticsStatus != newStatus) {
            throw new IllegalStateException("物流单已处于终态[" + this.logisticsStatus.getDescription()
                    + "]，不允许再变更为[" + newStatus.getDescription() + "]");
        }
        LogisticsStatus oldStatus = this.logisticsStatus;
        this.logisticsStatus = newStatus;
        this.updatedAt = LocalDateTime.now();
        if (newStatus == LogisticsStatus.DELIVERED && this.deliveredAt == null) {
            this.deliveredAt = this.updatedAt;
        }
        appendTrace(new LogisticsTrace(this.updatedAt, newStatus.name(),
                description == null ? newStatus.getDescription() : description, null));

        registerDomainEvent(LogisticsStatusChangedEvent.builder()
                .logisticsOrderId(this.logisticsOrderId)
                .orderId(this.orderId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedAt(this.updatedAt)
                .build());

        if (newStatus.isDelivered()) {
            registerDomainEvent(LogisticsCompletedEvent.builder()
                    .logisticsOrderId(this.logisticsOrderId)
                    .orderId(this.orderId)
                    .completedAt(this.updatedAt)
                    .build());
        }
    }

    /**
     * 生成物流标签并流转到「已打单」。
     *
     * @param label 物流标签
     */
    public void generateLabel(LogisticsLabel label) {
        this.logisticsLabel = label;
        registerDomainEvent(LogisticsLabelGeneratedEvent.builder()
                .logisticsOrderId(this.logisticsOrderId)
                .orderId(this.orderId)
                .labelId(label.getLabelId())
                .trackingNumber(this.trackingNumber)
                .generatedAt(LocalDateTime.now())
                .build());
        updateStatus(LogisticsStatus.LABEL_GENERATED, "已打单");
    }

    /**
     * 标记为已发货（揽收），记录发货时间。
     *
     * @param shippedAt 发货时间，为空时取当前时间
     */
    public void markShipped(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt == null ? LocalDateTime.now() : shippedAt;
        updateStatus(LogisticsStatus.COLLECTED, "已揽收发货");
    }

    /**
     * 设置运单号。
     */
    public void assignTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return;
        }
        this.trackingNumber = trackingNumber.trim();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 绑定第三方平台来源信息，用于导入幂等。
     */
    public void bindPlatformSource(String platform, String externalLogisticsId) {
        this.platform = platform;
        this.externalLogisticsId = externalLogisticsId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 绑定第三方平台回传的面单信息，不改变物流状态。
     */
    public void attachLabel(LogisticsLabel label) {
        this.logisticsLabel = label;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 登记平台回传的发货时间（不推进状态，状态由 {@link #updateStatus} 负责）。
     */
    public void recordShippedAt(LocalDateTime shippedAt) {
        if (shippedAt != null) {
            this.shippedAt = shippedAt;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 登记平台回传的预计送达时间。
     */
    public void recordEstimatedArrivalAt(LocalDateTime estimatedArrivalAt) {
        if (estimatedArrivalAt != null) {
            this.estimatedArrivalAt = estimatedArrivalAt;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 登记平台回传的签收时间。
     */
    public void recordDeliveredAt(LocalDateTime deliveredAt) {
        if (deliveredAt != null) {
            this.deliveredAt = deliveredAt;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 追加轨迹节点（保留最新100条）。
     */
    public void appendTrace(LogisticsTrace trace) {
        if (trace == null || trace.description() == null || trace.description().isBlank()) {
            return;
        }
        this.traces.add(trace);
        while (this.traces.size() > 100) {
            this.traces.remove(0);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    private void registerDomainEvent(Object event) {
        domainEvents.add(event);
    }

    public String getLogisticsOrderId() {
        return logisticsOrderId;
    }

    public void setLogisticsOrderId(String logisticsOrderId) {
        this.logisticsOrderId = logisticsOrderId;
    }

    public String getLogisticsOrderNumber() {
        return logisticsOrderNumber;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getLogisticsChannelId() {
        return logisticsChannelId;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public String getExternalLogisticsId() {
        return externalLogisticsId;
    }

    public LogisticsStatus getLogisticsStatus() {
        return logisticsStatus;
    }

    public LogisticsLabel getLogisticsLabel() {
        return logisticsLabel;
    }

    public LogisticsFee getLogisticsFee() {
        return logisticsFee;
    }

    public void setLogisticsFee(LogisticsFee fee) {
        this.logisticsFee = fee;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public LocalDateTime getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public List<LogisticsTrace> getTraces() {
        return List.copyOf(traces);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 物流单构建器。
     */
    public static class Builder {
        private String logisticsOrderId;
        private String logisticsOrderNumber;
        private String tenantId;
        private String orderId;
        private String logisticsChannelId;
        private String carrier;
        private String trackingNumber;
        private String trackingUrl;
        private String platform;
        private String externalLogisticsId;
        private LogisticsStatus logisticsStatus;
        private LogisticsLabel logisticsLabel;
        private LogisticsFee logisticsFee;
        private LocalDateTime shippedAt;
        private LocalDateTime estimatedArrivalAt;
        private LocalDateTime deliveredAt;
        private List<LogisticsTrace> traces;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder logisticsOrderId(String logisticsOrderId) {
            this.logisticsOrderId = logisticsOrderId;
            return this;
        }

        public Builder logisticsOrderNumber(String logisticsOrderNumber) {
            this.logisticsOrderNumber = logisticsOrderNumber;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder logisticsChannelId(String logisticsChannelId) {
            this.logisticsChannelId = logisticsChannelId;
            return this;
        }

        public Builder carrier(String carrier) {
            this.carrier = carrier;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder trackingUrl(String trackingUrl) {
            this.trackingUrl = trackingUrl;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder externalLogisticsId(String externalLogisticsId) {
            this.externalLogisticsId = externalLogisticsId;
            return this;
        }

        public Builder logisticsStatus(LogisticsStatus logisticsStatus) {
            this.logisticsStatus = logisticsStatus;
            return this;
        }

        public Builder logisticsLabel(LogisticsLabel logisticsLabel) {
            this.logisticsLabel = logisticsLabel;
            return this;
        }

        public Builder logisticsFee(LogisticsFee logisticsFee) {
            this.logisticsFee = logisticsFee;
            return this;
        }

        public Builder shippedAt(LocalDateTime shippedAt) {
            this.shippedAt = shippedAt;
            return this;
        }

        public Builder estimatedArrivalAt(LocalDateTime estimatedArrivalAt) {
            this.estimatedArrivalAt = estimatedArrivalAt;
            return this;
        }

        public Builder deliveredAt(LocalDateTime deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public Builder traces(List<LogisticsTrace> traces) {
            this.traces = traces;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public LogisticsOrder build() {
            return new LogisticsOrder(this);
        }
    }
}
