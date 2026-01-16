package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 物流单JPA实体，用于持久化物流单聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_orders")
@Getter
@Setter
public class LogisticsOrderJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "logistics_order_number", unique = true, nullable = false)
    private String logisticsOrderNumber;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logistics_channel_id", nullable = false)
    private LogisticsChannelJpaEntity logisticsChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LogisticsStatusJpaEntity status;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "logistics_label_id")
    private LogisticsLabelJpaEntity logisticsLabel;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "logistics_fee_id")
    private LogisticsFeeJpaEntity logisticsFee;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    // Getter methods
    public String getId() {
        return id;
    }

    // Setter methods
    public void setId(String id) {
        this.id = id;
    }

    public String getLogisticsOrderNumber() {
        return logisticsOrderNumber;
    }

    public void setLogisticsOrderNumber(String logisticsOrderNumber) {
        this.logisticsOrderNumber = logisticsOrderNumber;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public LogisticsChannelJpaEntity getLogisticsChannel() {
        return logisticsChannel;
    }

    public void setLogisticsChannel(LogisticsChannelJpaEntity logisticsChannel) {
        this.logisticsChannel = logisticsChannel;
    }

    public LogisticsStatusJpaEntity getStatus() {
        return status;
    }

    public void setStatus(LogisticsStatusJpaEntity status) {
        this.status = status;
    }

    public LogisticsLabelJpaEntity getLogisticsLabel() {
        return logisticsLabel;
    }

    public void setLogisticsLabel(LogisticsLabelJpaEntity logisticsLabel) {
        this.logisticsLabel = logisticsLabel;
    }

    public LogisticsFeeJpaEntity getLogisticsFee() {
        return logisticsFee;
    }

    public void setLogisticsFee(LogisticsFeeJpaEntity logisticsFee) {
        this.logisticsFee = logisticsFee;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
