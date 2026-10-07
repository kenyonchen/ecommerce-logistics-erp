package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import java.util.List;
import java.util.Optional;

/**
 * 订单导入的标准目标字段及其常见别名。
 * 第三方电商平台返回的字段命名各不相同，通过该枚举建立「平台原始字段 -> 标准订单字段」的映射基础。
 *
 * @author kenyon
 */
public enum OrderImportTargetField {
    EXTERNAL_ORDER_ID("平台订单号", true,
            "externalOrderId", "external_order_id", "orderId", "order_id", "amazonOrderId", "amazon-order-id",
            "orderNumber", "order_number", "orderNo", "order_no", "platformOrderId", "订单号", "平台订单号"),
    ORDER_NUMBER("系统订单号", false, "salesOrderNumber", "systemOrderNumber", "system_order_number", "系统订单号"),
    CUSTOMER_ID("客户ID", false, "customerId", "customer_id", "buyerId", "buyer_id", "userId", "user_id", "客户ID"),
    CUSTOMER_NAME("客户名称", false,
            "customerName", "customer_name", "buyerName", "buyer_name", "buyer", "customer", "客户名称", "买家", "客户"),
    CUSTOMER_EMAIL("客户邮箱", false,
            "customerEmail", "customer_email", "buyerEmail", "buyer_email", "email", "邮箱"),
    ORDER_STATUS("订单状态", false, "orderStatus", "order_status", "status", "订单状态", "状态"),
    CURRENCY("币种", false, "currency", "currencyCode", "currency_code", "币种", "货币"),
    ORDER_TIME("下单时间", false,
            "orderTime", "order_time", "purchaseDate", "purchase_date", "createdAt", "created_at",
            "orderDate", "order_date", "下单时间"),
    PAYMENT_METHOD("支付方式", false, "paymentMethod", "payment_method", "paymentType", "payment_type", "支付方式"),
    PAYMENT_STATUS("支付状态", false, "paymentStatus", "payment_status", "paymentState", "payStatus", "支付状态"),
    TRANSACTION_ID("交易号", false, "transactionId", "transaction_id", "paymentTransactionId", "tradeNo", "交易号"),
    TOTAL_AMOUNT("订单总金额", false,
            "totalAmount", "total_amount", "orderTotal", "order_total", "amount", "grandTotal", "订单金额", "总金额"),
    SHIPPING_RECIPIENT("收件人", false, "recipientName", "recipient_name", "shipName", "receiverName", "收件人"),
    SHIPPING_PHONE("联系电话", false, "phone", "phoneNumber", "phone_number", "shipPhone", "tel", "联系电话", "电话"),
    SHIPPING_COUNTRY("国家", false, "country", "countryCode", "shipCountry", "国家"),
    SHIPPING_PROVINCE("省/州", false, "province", "state", "stateOrProvince", "state_province", "省", "州"),
    SHIPPING_CITY("城市", false, "city", "shipCity", "城市"),
    SHIPPING_STREET("详细地址", false, "street", "addressLine1", "address_line1", "address", "streetAddress", "地址", "详细地址"),
    SHIPPING_ZIPCODE("邮编", false, "zipCode", "zip_code", "postalCode", "postal_code", "zip", "邮编"),
    ITEMS("订单明细", false, "items", "lineItems", "line_items", "orderItems", "order_items", "明细", "订单明细"),
    ITEM_SKU("SKU编码", false, "sku", "skuCode", "sku_code", "sellerSku", "seller-sku", "variantSku", "SKU"),
    ITEM_NAME("商品名称", false, "productName", "product_name", "itemName", "item_name", "title", "商品名称"),
    ITEM_QUANTITY("数量", false, "quantity", "qty", "itemQuantity", "数量"),
    ITEM_UNIT_PRICE("单价", false, "unitPrice", "unit_price", "price", "itemPrice", "单价");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    OrderImportTargetField(String label, boolean required, String... aliases) {
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
    public static Optional<OrderImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (OrderImportTargetField field : values()) {
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
    public static Optional<OrderImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (OrderImportTargetField field : values()) {
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
