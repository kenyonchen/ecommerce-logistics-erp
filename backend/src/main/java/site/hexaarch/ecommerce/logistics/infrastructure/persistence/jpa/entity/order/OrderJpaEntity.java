package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 订单JPA实体，用于持久化订单聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_platform_order",
                columnNames = {"tenant_id", "platform", "platform_order_id"}),
        indexes = {
                @Index(name = "idx_orders_tenant", columnList = "tenant_id"),
                @Index(name = "idx_orders_customer", columnList = "customer_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_created_at", columnList = "created_at")
        })
@Getter
@Setter
public class OrderJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "order_number", unique = true, nullable = false, length = 64)
    private String orderNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatusJpaEntity status;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "shipping_address_id")
    private ShippingAddressJpaEntity shippingAddress;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_info_id")
    private PaymentInfoJpaEntity paymentInfo;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemJpaEntity> orderItems;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "customer_email", length = 128)
    private String customerEmail;

    @Column(name = "total_amount")
    private Double totalAmount;

    /**
     * 来源平台（AMAZON/EBAY/SHOPIFY...、MANUAL、FILE）
     */
    @Column(name = "platform", length = 32)
    private String platform;

    /**
     * 第三方平台订单号，与 tenant_id + platform 组成幂等唯一键
     */
    @Column(name = "platform_order_id", length = 128)
    private String platformOrderId;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;
}
