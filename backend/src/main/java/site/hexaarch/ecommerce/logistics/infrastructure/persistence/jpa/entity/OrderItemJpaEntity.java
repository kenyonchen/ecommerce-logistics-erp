package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单行项JPA实体，用于持久化订单行项。
 *
 * @author kenyon
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItemJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;
}
