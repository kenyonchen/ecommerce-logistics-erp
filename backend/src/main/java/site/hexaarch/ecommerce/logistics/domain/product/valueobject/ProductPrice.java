package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 产品价格值对象，包含销售价格、成本价格和货币。
 *
 * @author kenyon
 */
@Getter
public class ProductPrice {
    /**
     * 销售价格
     */
    private final BigDecimal sellingPrice;
    /**
     * 成本价格
     */
    private final BigDecimal costPrice;
    /**
     * 货币类型
     */
    private final String currency;

    private ProductPrice(BigDecimal sellingPrice, BigDecimal costPrice, String currency) {
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.currency = currency;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BigDecimal sellingPrice;
        private BigDecimal costPrice;
        private String currency;

        public Builder sellingPrice(BigDecimal sellingPrice) {
            this.sellingPrice = sellingPrice;
            return this;
        }

        public Builder costPrice(BigDecimal costPrice) {
            this.costPrice = costPrice;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public ProductPrice build() {
            return new ProductPrice(sellingPrice, costPrice, currency);
        }
    }
}
