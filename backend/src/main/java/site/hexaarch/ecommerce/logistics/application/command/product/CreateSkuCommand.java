package site.hexaarch.ecommerce.logistics.application.command.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建SKU命令。
 *
 * @author kenyon
 */
public record CreateSkuCommand(
        String skuCode,
        String skuName,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        String currency,
        Integer weightInGrams,
        Integer volumeInCubicCentimeters,
        List<ProductAttributeCommand> attributes,
        Boolean active
) {
}
