package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product;

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

    /**
     * 销售单价
     */
    @Column(name = "price")
    private Double sellingPrice;

    /**
     * 成本单价
     */
    @Column(name = "cost_price")
    private Double costPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    /**
     * 重量，单位：克
     */
    @Column(name = "weight")
    private Double weightInGrams;

    /**
     * 体积，单位：立方厘米
     */
    @Column(name = "volume_cc")
    private Integer volumeInCubicCentimeters;

    /**
     * SKU属性（颜色/容量等变体属性）的JSON序列化结果
     */
    @Column(name = "attributes_json", length = 4000)
    private String attributesJson;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public SkuJpaEntity() {
        this.active = true;
    }
}
