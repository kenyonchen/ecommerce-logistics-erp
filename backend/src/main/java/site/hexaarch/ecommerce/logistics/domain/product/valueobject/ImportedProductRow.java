package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 经过字段映射后的标准化商品行，是第三方平台数据进入领域模型之前的中间结构。
 *
 * @author kenyon
 */
public record ImportedProductRow(
        int rowNumber,
        String externalProductId,
        String externalSkuId,
        String name,
        String description,
        String categoryName,
        String skuCode,
        String skuName,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        String currency,
        Integer weightInGrams,
        Integer volumeInCubicCentimeters,
        List<ProductAttribute> attributes,
        Boolean active
) {
    public ImportedProductRow {
        if (attributes == null) {
            attributes = List.of();
        }
    }
}
