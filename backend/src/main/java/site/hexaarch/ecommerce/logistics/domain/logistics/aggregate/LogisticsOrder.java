package site.hexaarch.ecommerce.logistics.domain.logistics.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsLabelGeneratedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsOrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 物流单聚合根，是物流单聚合的核心实体，负责管理物流状态、物流标签、物流费用等。
 *
 * @author kenyon
 */
@Getter
public class LogisticsOrder {
    protected List<Object> domainEvents;
    private String logisticsOrderId;
    private String tenantId;
    private String orderId;
    private String logisticsChannelId;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private LogisticsStatus logisticsStatus;
    private LogisticsLabel logisticsLabel;
    private LogisticsFee logisticsFee;
    private LocalDateTime updatedAt;

    /**
     * 受保护的无参构造函数，用于JPA和MapStruct。
     */
    protected LogisticsOrder() {
        this.domainEvents = new ArrayList<>();
    }

    /**
     * 私有构造函数，防止直接实例化。
     *
     * @param logisticsOrderId   物流单ID
     * @param tenantId           租户ID
     * @param orderId            订单ID
     * @param logisticsChannelId 物流渠道ID
     * @param logisticsStatus    物流状态
     * @param trackingNumber     运单号
     * @param logisticsLabel     物流标签
     * @param logisticsFee       物流费用
     * @param createdAt          创建时间
     * @param updatedAt          更新时间
     */
    private LogisticsOrder(String logisticsOrderId, String tenantId, String orderId, String logisticsChannelId, LogisticsStatus logisticsStatus, String trackingNumber, LogisticsLabel logisticsLabel, LogisticsFee logisticsFee, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.logisticsOrderId = logisticsOrderId != null ? logisticsOrderId : UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.logisticsChannelId = Objects.requireNonNull(logisticsChannelId, "Logistics channel ID cannot be null");
        this.logisticsStatus = logisticsStatus != null ? logisticsStatus : LogisticsStatus.PENDING;
        this.trackingNumber = trackingNumber != null ? trackingNumber : generateTrackingNumber();
        this.logisticsLabel = logisticsLabel;
        this.logisticsFee = logisticsFee;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.domainEvents = new ArrayList<>(); // 初始化domainEvents
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
        LogisticsOrder logisticsOrder = new LogisticsOrder(
                UUID.randomUUID().toString(), // logisticsOrderId
                tenantId, // tenantId
                orderId, // orderId
                logisticsChannelId, // logisticsChannelId
                LogisticsStatus.PENDING, // logisticsStatus
                generateTrackingNumber(), // trackingNumber
                null, // logisticsLabel
                null, // logisticsFee
                LocalDateTime.now(), // createdAt
                LocalDateTime.now() // updatedAt
        );

        // 注册物流单创建事件
        LogisticsOrderCreatedEvent event = LogisticsOrderCreatedEvent.builder()
                .logisticsOrderId(logisticsOrder.getLogisticsOrderId())
                .tenantId(logisticsOrder.getTenantId())
                .orderId(logisticsOrder.getOrderId())
                .logisticsChannelId(logisticsOrder.getLogisticsChannelId())
                .trackingNumber(logisticsOrder.getTrackingNumber())
                .createdAt(logisticsOrder.getCreatedAt())
                .build();
        logisticsOrder.registerDomainEvent(event);

        return logisticsOrder;
    }

    /**
     * 生成运单号。
     *
     * @return 运单号
     */
    private static String generateTrackingNumber() {
        // 简单生成一个运单号，实际项目中应该使用物流渠道提供的API生成
        return "TN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static LogisticsOrderBuilder builder() {
        return new LogisticsOrderBuilder();
    }

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }

    protected void registerDomainEvent(Object event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    /**
     * 更新物流状态。
     *
     * @param newStatus 新的物流状态
     */
    public void updateStatus(LogisticsStatus newStatus) {
        LogisticsStatus oldStatus = this.logisticsStatus;
        this.logisticsStatus = newStatus;
        this.updatedAt = LocalDateTime.now();

        // 注册物流状态变更事件
        LogisticsStatusChangedEvent event = LogisticsStatusChangedEvent.builder()
                .logisticsOrderId(this.getLogisticsOrderId())
                .orderId(this.getOrderId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedAt(this.getUpdatedAt())
                .build();
        this.registerDomainEvent(event);

        // 如果物流状态变为已送达，注册物流完成事件
        if (newStatus.isDelivered()) {
            LogisticsCompletedEvent completedEvent = LogisticsCompletedEvent.builder()
                    .logisticsOrderId(this.getLogisticsOrderId())
                    .orderId(this.getOrderId())
                    .completedAt(this.getUpdatedAt())
                    .build();
            this.registerDomainEvent(completedEvent);
        }
    }

    /**
     * 生成物流标签。
     *
     * @param label 物流标签
     */
    public void generateLabel(LogisticsLabel label) {
        this.logisticsLabel = label;
        this.updatedAt = LocalDateTime.now();

        // 注册物流标签生成事件
        LogisticsLabelGeneratedEvent event = LogisticsLabelGeneratedEvent.builder()
                .logisticsOrderId(this.getLogisticsOrderId())
                .orderId(this.getOrderId())
                .labelId(label.getLabelId())
                .trackingNumber(this.getTrackingNumber())
                .generatedAt(this.getUpdatedAt())
                .build();
        this.registerDomainEvent(event);
    }

    protected void setLogisticsFeeForFramework(LogisticsFee logisticsFee) {
        this.logisticsFee = logisticsFee;
    }

    public String getLogisticsOrderId() {
        return logisticsOrderId;
    }

    // 为JPA和MapStruct添加setter方法
    protected void setLogisticsOrderId(String logisticsOrderId) {
        this.logisticsOrderId = logisticsOrderId;
    }

    public String getOrderId() {
        return orderId;
    }

    protected void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getLogisticsChannelId() {
        return logisticsChannelId;
    }

    protected void setLogisticsChannelId(String logisticsChannelId) {
        this.logisticsChannelId = logisticsChannelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    protected void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    protected void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LogisticsStatus getLogisticsStatus() {
        return logisticsStatus;
    }

    protected void setLogisticsStatus(LogisticsStatus logisticsStatus) {
        this.logisticsStatus = logisticsStatus;
    }

    public LogisticsLabel getLogisticsLabel() {
        return logisticsLabel;
    }

    protected void setLogisticsLabel(LogisticsLabel logisticsLabel) {
        this.logisticsLabel = logisticsLabel;
    }

    public LogisticsFee getLogisticsFee() {
        return logisticsFee;
    }

    /**
     * 设置物流费用。
     *
     * @param fee 物流费用
     */
    public void setLogisticsFee(LogisticsFee fee) {
        this.logisticsFee = fee;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    protected void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public static class LogisticsOrderBuilder {
        private String logisticsOrderId;
        private String tenantId;
        private String orderId;
        private String logisticsChannelId;
        private LogisticsStatus logisticsStatus;
        private String trackingNumber;
        private LogisticsLabel logisticsLabel;
        private LogisticsFee logisticsFee;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public LogisticsOrderBuilder logisticsOrderId(String logisticsOrderId) {
            this.logisticsOrderId = logisticsOrderId;
            return this;
        }

        public LogisticsOrderBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public LogisticsOrderBuilder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public LogisticsOrderBuilder logisticsChannelId(String logisticsChannelId) {
            this.logisticsChannelId = logisticsChannelId;
            return this;
        }

        public LogisticsOrderBuilder logisticsStatus(LogisticsStatus logisticsStatus) {
            this.logisticsStatus = logisticsStatus;
            return this;
        }

        public LogisticsOrderBuilder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public LogisticsOrderBuilder logisticsLabel(LogisticsLabel logisticsLabel) {
            this.logisticsLabel = logisticsLabel;
            return this;
        }

        public LogisticsOrderBuilder logisticsFee(LogisticsFee logisticsFee) {
            this.logisticsFee = logisticsFee;
            return this;
        }

        public LogisticsOrderBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public LogisticsOrderBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public LogisticsOrder build() {
            return new LogisticsOrder(
                    logisticsOrderId != null ? logisticsOrderId : java.util.UUID.randomUUID().toString(),
                    tenantId,
                    orderId,
                    logisticsChannelId,
                    logisticsStatus != null ? logisticsStatus : LogisticsStatus.PENDING,
                    trackingNumber != null ? trackingNumber : generateTrackingNumber(),
                    logisticsLabel,
                    logisticsFee,
                    createdAt != null ? createdAt : java.time.LocalDateTime.now(),
                    updatedAt != null ? updatedAt : java.time.LocalDateTime.now()
            );
        }
    }
}