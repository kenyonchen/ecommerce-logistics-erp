package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物流单JPA实体，用于持久化物流单聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_orders", indexes = {
        @Index(name = "idx_logistics_orders_tenant", columnList = "tenant_id"),
        @Index(name = "idx_logistics_orders_order", columnList = "order_id"),
        @Index(name = "idx_logistics_orders_tracking", columnList = "tracking_number")})
@Getter
@Setter
public class LogisticsOrderJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "logistics_order_number", unique = true, nullable = false)
    private String logisticsOrderNumber;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logistics_channel_id", nullable = false)
    private LogisticsChannelJpaEntity logisticsChannel;

    @Column(name = "carrier")
    private String carrier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private LogisticsStatusJpaEntity status;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "logistics_label_id")
    private LogisticsLabelJpaEntity logisticsLabel;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "logistics_fee_id")
    private LogisticsFeeJpaEntity logisticsFee;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "tracking_url", length = 512)
    private String trackingUrl;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "external_logistics_id", length = 128)
    private String externalLogisticsId;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "estimated_arrival_at")
    private LocalDateTime estimatedArrivalAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * 轨迹节点列表，以JSON数组文本存储
     */
    @Column(name = "tracking_traces", length = 4000)
    private String trackingTraces;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;
}
