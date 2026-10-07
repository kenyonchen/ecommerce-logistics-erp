package site.hexaarch.ecommerce.logistics.domain.customer.valueobject;

import java.util.List;
import java.util.Optional;

/**
 * 客户导入的标准目标字段及其常见别名。
 * 第三方电商平台返回的买家字段命名各不相同，通过该枚举建立「平台原始字段 -> 标准客户字段」的映射基础。
 * <p>
 * 注意：枚举声明顺序影响 detectByAlias 的精确匹配优先级，请勿随意调整。
 * </p>
 *
 * @author kenyon
 */
public enum CustomerImportTargetField {
    EXTERNAL_BUYER_ID("平台买家ID", false,
            "externalBuyerId", "external_buyer_id", "buyerId", "buyer_id", "customerId", "customer_id",
            "userId", "user_id", "买家ID", "平台买家ID"),
    CUSTOMER_NAME("客户名称", true,
            "customerName", "customer_name", "buyerName", "buyer_name", "buyer", "customer", "name",
            "contactName", "客户名称", "买家", "联系人"),
    CUSTOMER_EMAIL("客户邮箱", false,
            "customerEmail", "customer_email", "buyerEmail", "buyer_email", "email", "邮箱", "客户邮箱"),
    CUSTOMER_PHONE("客户电话", false,
            "customerPhone", "phone", "phoneNumber", "phone_number", "buyerPhone", "tel", "mobile",
            "联系电话", "电话"),
    COMPANY("公司", false, "company", "companyName", "company_name", "公司"),
    COUNTRY("国家", false, "country", "countryCode", "shipCountry", "国家"),
    PROVINCE("省/州", false, "province", "state", "stateOrProvince", "state_province", "省", "州"),
    CITY("城市", false, "city", "shipCity", "城市"),
    ADDRESS("详细地址", false,
            "address", "addressLine1", "address_line1", "street", "streetAddress", "fullAddress",
            "详细地址", "地址");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    CustomerImportTargetField(String label, boolean required, String... aliases) {
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
    public static Optional<CustomerImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (CustomerImportTargetField field : values()) {
            for (String alias : field.aliases) {
                if (normalized.equals(normalize(alias))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 候选别名匹配：允许包含关系，如 "buyer-email-address" 命中 CUSTOMER_EMAIL
     */
    public static Optional<CustomerImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (CustomerImportTargetField field : values()) {
            for (String alias : field.aliases) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.length() >= 3
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
