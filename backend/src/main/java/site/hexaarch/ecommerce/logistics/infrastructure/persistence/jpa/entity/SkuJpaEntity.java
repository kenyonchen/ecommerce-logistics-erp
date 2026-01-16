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
 * SKU JPA实体，用于持久化SKU信息。
 *
 * @author kenyon
 */
@Entity
@Table(name = "skus")
@Getter
@Setter
public class SkuJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "sku_code", nullable = false, unique = true)
    private String skuCode;

    @Column(name = "sku_name", nullable = false)
    private String skuName;

    @Column(name = "price")
    private Double price;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "dimensions")
    private String dimensions;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public SkuJpaEntity() {
        this.active = true;
    }
}