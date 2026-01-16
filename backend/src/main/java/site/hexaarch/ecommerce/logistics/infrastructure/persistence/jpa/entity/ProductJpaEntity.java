package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 产品JPA实体，用于持久化产品聚合。
 *
 * @author kenyon
 */
@Entity
@Table(name = "products")
@Getter
@Setter
public class ProductJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkuJpaEntity> skus;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public ProductJpaEntity() {
        this.active = true;
    }

    // Getter methods
    public String getId() {
        return id;
    }

    // Setter methods
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<SkuJpaEntity> getSkus() {
        return skus;
    }

    public void setSkus(List<SkuJpaEntity> skus) {
        this.skus = skus;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}