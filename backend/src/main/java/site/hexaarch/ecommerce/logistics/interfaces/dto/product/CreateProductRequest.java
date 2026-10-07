package site.hexaarch.ecommerce.logistics.interfaces.dto.product;

import java.util.List;

/**
 * 创建商品请求体。
 *
 * @author kenyon
 */
public class CreateProductRequest {
    /**
     * 租户ID，缺省时使用 default-tenant
     */
    private String tenantId;
    private String name;
    private String description;
    /**
     * 分类名称（未提供分类ID时为必填）
     */
    private String categoryName;
    /**
     * 分类ID，未提供时自动生成
     */
    private String categoryId;
    private Boolean active;
    private List<SkuRequestDto> skus;

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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<SkuRequestDto> getSkus() {
        return skus;
    }

    public void setSkus(List<SkuRequestDto> skus) {
        this.skus = skus;
    }
}
