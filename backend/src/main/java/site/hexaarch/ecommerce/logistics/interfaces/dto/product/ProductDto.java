package site.hexaarch.ecommerce.logistics.interfaces.dto.product;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品数据传输对象，用于向外部系统提供产品信息。
 * 前后端统一结构：分类平铺为 categoryId / categoryName，SKU 明细内嵌返回。
 *
 * @author kenyon
 */
public class ProductDto {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName;
    /**
     * 来源平台（第三方电商平台名称），手动创建时为空
     */
    private String sourcePlatform;
    /**
     * 来源平台侧的商品ID
     */
    private String sourceProductId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductSkuDto> skus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceProductId() {
        return sourceProductId;
    }

    public void setSourceProductId(String sourceProductId) {
        this.sourceProductId = sourceProductId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProductSkuDto> getSkus() {
        return skus;
    }

    public void setSkus(List<ProductSkuDto> skus) {
        this.skus = skus;
    }
}
