package site.hexaarch.ecommerce.logistics.domain.order.entity;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单行项实体，表示订单中的商品明细。
 *
 * @author kenyon
 */
@Getter
public class OrderItem {
    private final String id;
    private final String productId;
    private final String sku;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;

    private OrderItem(String id, String productId, String sku, String productName, int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建新的订单行项。
     *
     * @param productId   产品ID
     * @param sku         SKU
     * @param productName 产品名称
     * @param quantity    数量
     * @param unitPrice   单价
     * @return 新的订单行项
     */
    public static OrderItem create(String productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
        return OrderItem.builder()
                .id(java.util.UUID.randomUUID().toString())
                .productId(productId)
                .sku(sku)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public static class Builder {
        private String id;
        private String productId;
        private String sku;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder totalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }

        public OrderItem build() {
            return new OrderItem(id, productId, sku, productName, quantity, unitPrice, totalPrice);
        }

        public Builder toBuilder() {
            return this;
        }
    }
}