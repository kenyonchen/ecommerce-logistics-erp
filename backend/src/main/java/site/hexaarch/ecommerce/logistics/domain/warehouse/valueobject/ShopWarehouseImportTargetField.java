package site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject;

import java.util.Arrays;
import java.util.List;

/**
 * 店铺仓库导入的标准目标字段及其常见别名。
 * 第三方电商平台返回的仓库字段命名各不相同，通过该枚举建立「平台原始字段 -> 标准店铺仓库字段」的映射基础。
 *
 * @author kenyon
 */
public enum ShopWarehouseImportTargetField {
    SHOP_CODE("店铺编码", false, "shopCode", "shop_code", "storeCode", "store_code", "shopId", "shop_id", "storeId", "store_id", "店铺编码", "店铺ID"),
    SHOP_NAME("店铺名称", false, "shopName", "shop_name", "storeName", "store_name", "店铺名称", "店铺"),
    PLATFORM("平台", false, "platform", "platformName", "platform_name", "channel", "平台", "渠道"),
    PLATFORM_WAREHOUSE_ID("平台仓库ID", true, "platformWarehouseId", "platform_warehouse_id", "warehouseId", "warehouse_id", "externalWarehouseId", "external_warehouse_id", "wid", "平台仓库ID", "仓库ID"),
    PLATFORM_WAREHOUSE_NAME("平台仓库名称", false, "platformWarehouseName", "platform_warehouse_name", "warehouseName", "warehouse_name", "name", "title", "平台仓库名称", "仓库名称"),
    WAREHOUSE_CODE("本地仓库编码", true, "warehouseCode", "warehouse_code", "localWarehouseCode", "local_warehouse_code", "仓库编码", "本地仓库编码"),
    ADDRESS("仓库地址", false, "address", "warehouseAddress", "warehouse_address", "location", "fullAddress", "地址", "仓库地址"),
    IS_DEFAULT("是否默认仓库", false, "isDefault", "is_default", "default", "defaultFlag", "default_flag", "是否默认", "默认仓库"),
    ACTIVE("是否启用", false, "active", "enabled", "isActive", "status", "启用状态", "状态", "是否启用");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    ShopWarehouseImportTargetField(String label, boolean required, String... aliases) {
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
    public static java.util.Optional<ShopWarehouseImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (ShopWarehouseImportTargetField field : values()) {
            for (String alias : field.aliases) {
                if (normalized.equals(normalize(alias))) {
                    return java.util.Optional.of(field);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * 候选别名匹配：允许包含关系
     */
    public static java.util.Optional<ShopWarehouseImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (ShopWarehouseImportTargetField field : values()) {
            for (String alias : field.aliases) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.length() >= 4
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return java.util.Optional.of(field);
                }
            }
        }
        return java.util.Optional.empty();
    }

    public static List<ShopWarehouseImportTargetField> requiredFields() {
        return Arrays.stream(values()).filter(ShopWarehouseImportTargetField::isRequired).toList();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
