package site.hexaarch.ecommerce.logistics.interfaces.dto.product;

import java.util.List;

/**
 * 更新商品请求体。
 *
 * @author kenyon
 */
public class UpdateProductRequest {
    private String name;
    private String description;
    private String categoryName;
    private String categoryId;
    private Boolean active;
    /**
     * SKU整体覆盖更新，未提供时保持原有SKU不变
     */
    private List<SkuRequestDto> skus;

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
