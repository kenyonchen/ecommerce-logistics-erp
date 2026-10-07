package site.hexaarch.ecommerce.logistics.interfaces.dto.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU创建/更新请求项。
 *
 * @author kenyon
 */
public class SkuRequestDto {
    /**
     * SKU编码，同产品内唯一
     */
    private String skuCode;
    private String skuName;
    /**
     * 销售价（必填）
     */
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private String currency;
    /**
     * 重量，单位：克
     */
    private Integer weightInGrams;
    /**
     * 体积，单位：立方厘米
     */
    private Integer volumeInCubicCentimeters;
    private List<ProductAttributeDto> attributes;
    private Boolean active;

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getWeightInGrams() {
        return weightInGrams;
    }

    public void setWeightInGrams(Integer weightInGrams) {
        this.weightInGrams = weightInGrams;
    }

    public Integer getVolumeInCubicCentimeters() {
        return volumeInCubicCentimeters;
    }

    public void setVolumeInCubicCentimeters(Integer volumeInCubicCentimeters) {
        this.volumeInCubicCentimeters = volumeInCubicCentimeters;
    }

    public List<ProductAttributeDto> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<ProductAttributeDto> attributes) {
        this.attributes = attributes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
