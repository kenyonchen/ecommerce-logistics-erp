package site.hexaarch.ecommerce.logistics.domain.purchase.entity;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseEntity;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import java.math.BigDecimal;

/**
 * 采购单项实体，代表采购单中的一个商品项。
 *
 * @author kenyon
 */
@Getter
public class PurchaseOrderItem extends BaseEntity {
    private final String skuCode;
    private final String productName;
    private final Integer quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;

    /**
     * 私有构造函数，只能通过create方法创建。
     */
    private PurchaseOrderItem(String skuCode, String productName, Integer quantity, BigDecimal unitPrice) {
        this.skuCode = skuCode;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 创建采购单项。
     *
     * @param skuCode     SKU编码
     * @param productName 产品名称
     * @param quantity    数量
     * @param unitPrice   单价
     * @return 采购单项实例
     */
    public static PurchaseOrderItem create(String skuCode, String productName, Integer quantity, BigDecimal unitPrice) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("SKU编码(skuCode)不能为空");
        }
        if (productName == null || productName.isBlank()) {
            throw new ValidationException("产品名称(productName)不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("采购数量必须大于0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new ValidationException("采购单价必须大于0");
        }
        return new PurchaseOrderItem(skuCode.trim(), productName.trim(), quantity, unitPrice);
    }

    /**
     * 以新的数量/单价复制出同一条采购项（保留主键，供明细行修改使用）。
     *
     * @param newQuantity 新数量
     * @param newUnitPrice 新单价
     * @return 重新计算小计后的采购项
     */
    public PurchaseOrderItem updatedCopy(Integer newQuantity, BigDecimal newUnitPrice) {
        PurchaseOrderItem updated = create(skuCode, productName, newQuantity, newUnitPrice);
        updated.setId(getId());
        return updated;
    }

    // 手动添加builder方法
    public static Builder builder() {
        return new Builder();
    }

    // 手动添加toBuilder方法
    public Builder toBuilder() {
        return new Builder()
                .id(getId())
                .skuCode(this.skuCode)
                .productName(this.productName)
                .quantity(this.quantity)
                .unitPrice(this.unitPrice)
                .totalPrice(this.totalPrice);
    }

    public static class Builder {
        private String id;
        private String skuCode;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder skuCode(String skuCode) {
            this.skuCode = skuCode;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder quantity(Integer quantity) {
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

        public PurchaseOrderItem build() {
            var item = new PurchaseOrderItem(
                    this.skuCode,
                    this.productName,
                    this.quantity,
                    this.unitPrice
            );
            item.setId(this.id);
            return item;
        }
    }
}