package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品JPA实体，用于持久化产品聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_external_ref",
                columnNames = {"tenant_id", "source_platform", "source_product_id"}),
        indexes = {
                @Index(name = "idx_products_tenant", columnList = "tenant_id"),
                @Index(name = "idx_products_category", columnList = "category_name"),
                @Index(name = "idx_products_source", columnList = "source_platform")
        })
@Getter
@Setter
public class ProductJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "category_name")
    private String categoryName;

    /**
     * 来源平台（第三方电商平台名称），手动创建时为空
     */
    @Column(name = "source_platform")
    private String sourcePlatform;

    /**
     * 来源平台侧的商品ID，与 sourcePlatform 一起用于重复导入的幂等匹配
     */
    @Column(name = "source_product_id")
    private String sourceProductId;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkuJpaEntity> skus;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProductJpaEntity() {
        this.active = true;
    }
}
