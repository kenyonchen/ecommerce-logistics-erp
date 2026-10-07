package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

import java.util.Arrays;
import java.util.List;

/**
 * 商品导入的标准目标字段及其常见别名。
 * 第三方电商平台返回的字段命名各不相同，通过该枚举建立「平台原始字段 -> 标准商品字段」的映射基础。
 *
 * @author kenyon
 */
public enum ImportTargetField {
    PRODUCT_NAME("商品名称", true, "name", "productName", "product_name", "item_name", "item-name", "title", "productTitle", "商品名称", "产品名称"),
    PRODUCT_DESCRIPTION("商品描述", false, "description", "productDescription", "product_description", "product_detail", "商品描述", "描述"),
    CATEGORY_NAME("分类名称", false, "category", "categoryName", "category_name", "product_type", "productType", "categoryId", "分类", "类目"),
    SKU_CODE("SKU编码", true, "sku", "skuCode", "sku_code", "sellerSku", "seller-sku", "itemSku", "variationSku", "SKU", "sku码"),
    SKU_NAME("SKU名称", false, "skuName", "sku_name", "variationName", "variantTitle", "variant_name", "SKU名称"),
    SELLING_PRICE("销售价", false, "price", "sellingPrice", "selling_price", "unitPrice", "retailPrice", "售价", "销售价", "价格"),
    COST_PRICE("成本价", false, "costPrice", "cost_price", "cost", "purchasePrice", "wholesalePrice", "成本价", "成本"),
    CURRENCY("币种", false, "currency", "currencyCode", "currency_code", "币种", "货币"),
    WEIGHT_IN_GRAMS("重量(克)", false, "weight", "weightInGrams", "weight_g", "weightGrams", "item_weight", "重量"),
    VOLUME_IN_CC("体积(立方厘米)", false, "volume", "volumeInCubicCentimeters", "volume_cc", "dimensions", "体积", "尺寸"),
    ATTRIBUTES("变体属性", false, "attributes", "attributesJson", "variation_attributes", "attribute", "变体属性", "属性"),
    EXTERNAL_PRODUCT_ID("平台商品ID", false, "externalProductId", "external_product_id", "itemId", "item_id", "listingId", "listing_id", "productId", "product_id", "external_id", "商品ID"),
    EXTERNAL_SKU_ID("平台SKU ID", false, "externalSkuId", "external_sku_id", "skuId", "sku_id", "variantId", "variation_id"),
    ACTIVE("是否启用", false, "active", "enabled", "isActive", "status", "启用状态", "状态");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    ImportTargetField(String label, boolean required, String... aliases) {
        this.label = label;
        this.required = required;
        this.aliases = List.of(aliases);
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /**
     * 根据平台原始字段名自动推断目标字段（忽略大小写、连字符与下划线）
     */
    public static java.util.Optional<ImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (ImportTargetField field : values()) {
            for (String alias : field.aliases) {
                if (normalized.equals(normalize(alias))) {
                    return java.util.Optional.of(field);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * 候选别名匹配：允许包含关系，如 "item-weight-g" 命中 WEIGHT_IN_GRAMS
     */
    public static java.util.Optional<ImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (ImportTargetField field : values()) {
            for (String alias : field.aliases) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.length() >= 3
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return java.util.Optional.of(field);
                }
            }
        }
        return java.util.Optional.empty();
    }

    public static List<ImportTargetField> requiredFields() {
        return Arrays.stream(values()).filter(ImportTargetField::isRequired).toList();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
