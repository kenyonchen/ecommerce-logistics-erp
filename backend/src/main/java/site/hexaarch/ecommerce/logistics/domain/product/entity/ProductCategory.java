package site.hexaarch.ecommerce.logistics.domain.product.entity;

import lombok.Getter;

/**
 * 产品分类实体，对产品进行分类管理。
 *
 * @author kenyon
 */
@Getter
public class ProductCategory {
    /**
     * 分类唯一标识
     */
    private final String id;
    /**
     * 父分类ID
     */
    private final String parentId;
    /**
     * 分类层级
     */
    private final Integer level;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 分类描述
     */
    private String description;
    /**
     * 是否激活
     */
    private boolean active;

    private ProductCategory(String id, String parentId, Integer level, String name, String description, boolean active) {
        this.id = id;
        this.parentId = parentId;
        this.level = level;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 更新分类信息
     */
    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 激活分类
     */
    public void activate() {
        this.active = true;
    }

    /**
     * 停用分类
     */
    public void deactivate() {
        this.active = false;
    }

    public static class Builder {
        private String id;
        private String parentId;
        private Integer level;
        private String name;
        private String description;
        private boolean active;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder level(Integer level) {
            this.level = level;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public ProductCategory build() {
            return new ProductCategory(id, parentId, level, name, description, active);
        }
    }
}
