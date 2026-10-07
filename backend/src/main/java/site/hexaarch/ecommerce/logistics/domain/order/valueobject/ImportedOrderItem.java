package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import java.math.BigDecimal;

/**
 * 标准化后的订单行项（导入中间模型）。
 *
 * @author kenyon
 */
public record ImportedOrderItem(String sku,
                                String name,
                                int quantity,
                                BigDecimal unitPrice,
                                BigDecimal totalPrice) {

    public static ImportedOrderItem of(String sku, String name, int quantity, BigDecimal unitPrice) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        int safeQuantity = quantity <= 0 ? 1 : quantity;
        return new ImportedOrderItem(sku, name, safeQuantity, price, price.multiply(BigDecimal.valueOf(safeQuantity)));
    }
}
