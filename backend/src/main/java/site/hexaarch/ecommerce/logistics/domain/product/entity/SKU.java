package site.hexaarch.ecommerce.logistics.domain.product.entity;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductAttribute;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;

import java.util.List;

/**
 * SKU实体，产品的库存单位，是库存管理的最小单位。
 *
 * @author kenyon
 */
@Getter
public class SKU {
    /**
     * SKU唯一标识
     */
    private final String id;
    /**
     * SKU编码
     */
    private final String skuCode;
    /**
     * 关联的产品ID
     */
    private final String productId;
    /**
     * SKU名称（变体标题），为空时使用SKU编码
     */
    private String name;
    /**
     * 重量（克）
     */
    private final Integer weightInGrams;
    /**
     * 体积（立方厘米）
     */
    private final Integer volumeInCubicCentimeters;
    /**
     * SKU属性列表
     */
    private List<ProductAttribute> attributes;
    /**
     * SKU价格信息
     */
    private ProductPrice price;
    /**
     * 是否启用
     */
    private boolean active;

    private SKU(String id, String skuCode, String productId, Integer weightInGrams, Integer volumeInCubicCentimeters, List<ProductAttribute> attributes, ProductPrice price, String name, boolean active) {
        this.id = id;
        this.skuCode = skuCode;
        this.productId = productId;
        this.weightInGrams = weightInGrams;
        this.volumeInCubicCentimeters = volumeInCubicCentimeters;
        this.attributes = attributes;
        this.price = price;
        this.name = name;
        this.active = active;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 更新SKU价格
     */
    public void updatePrice(ProductPrice newPrice) {
        this.price = newPrice;
    }

    /**
     * 更新SKU属性
     */
    public void updateAttributes(List<ProductAttribute> newAttributes) {
        this.attributes = newAttributes;
    }

    /**
     * 获取SKU ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取SKU编码
     */
    public String getSkuCode() {
        return skuCode;
    }

    /**
     * 获取SKU名称，为空时回退为SKU编码
     */
    public String getName() {
        return (name == null || name.isBlank()) ? skuCode : name;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * 更新SKU名称
     */
    public void updateName(String name) {
        this.name = name;
    }

    /**
     * 启用/停用SKU
     */
    public void changeActive(boolean active) {
        this.active = active;
    }

    public static class Builder {
        private String id;
        private String skuCode;
        private String productId;
        private Integer weightInGrams;
        private Integer volumeInCubicCentimeters;
        private List<ProductAttribute> attributes;
        private ProductPrice price;
        private String name;
        private boolean active = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder skuCode(String skuCode) {
            this.skuCode = skuCode;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder weightInGrams(Integer weightInGrams) {
            this.weightInGrams = weightInGrams;
            return this;
        }

        public Builder volumeInCubicCentimeters(Integer volumeInCubicCentimeters) {
            this.volumeInCubicCentimeters = volumeInCubicCentimeters;
            return this;
        }

        public Builder attributes(List<ProductAttribute> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder price(ProductPrice price) {
            this.price = price;
            return this;
        }

        public SKU build() {
            return new SKU(id, skuCode, productId, weightInGrams, volumeInCubicCentimeters, attributes, price, name, active);
        }

        public Builder toBuilder() {
            return this;
        }
    }
}
