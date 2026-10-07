package site.hexaarch.ecommerce.logistics.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderImportError;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderRow;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 订单字段映射服务：负责平台原始字段与标准订单字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link OrderImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class OrderFieldMappingService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    );
    private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    private final OrderFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的订单行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedOrderRow row, List<OrderImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<OrderFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<OrderFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<OrderFieldMapping> enabled = custom.stream()
                    .filter(mapping -> mapping.getTargetField() != null && mapping.isEnabled())
                    .toList();
            if (!enabled.isEmpty()) {
                return enabled;
            }
        }
        return autoDetect(sourceFields);
    }

    /**
     * 基于标准字段别名自动推断映射关系
     */
    public List<OrderFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderFieldMapping> mappings = new ArrayList<>();
        List<OrderImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            OrderImportTargetField target = OrderImportTargetField.detectByAlias(sourceField)
                    .or(() -> OrderImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(OrderFieldMapping.create(null, null, sourceField, target, FieldTransform.NONE, null));
        }
        return mappings;
    }

    /**
     * 提取一行平台数据的所有表头字段，保持出现顺序
     */
    public List<String> detectSourceFields(List<Map<String, Object>> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (Map<String, Object> rawRow : rawRows) {
            if (rawRow == null) {
                continue;
            }
            for (String key : rawRow.keySet()) {
                if (key == null) {
                    continue;
                }
                normalizedToOriginal.putIfAbsent(normalizeKey(key), key);
            }
        }
        return new ArrayList<>(normalizedToOriginal.values());
    }

    /**
     * 将平台原始行转换为标准订单行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<OrderFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<OrderImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String externalOrderId = null;
        String orderNumber = null;
        String customerId = null;
        String customerName = null;
        String customerEmail = null;
        String currency = null;
        OrderStatus status = null;
        LocalDateTime orderedAt = null;
        String paymentMethod = null;
        String paymentStatus = null;
        String transactionId = null;
        BigDecimal totalAmount = null;
        String recipient = null;
        String phone = null;
        String country = null;
        String province = null;
        String city = null;
        String street = null;
        String zipCode = null;
        List<ImportedOrderItem> items = new ArrayList<>();

        Object rawItems = null;
        String itemSku = null;
        String itemName = null;
        Integer itemQuantity = null;
        BigDecimal itemUnitPrice = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (OrderFieldMapping mapping : mappings) {
            OrderImportTargetField target = mapping.getTargetField();
            if (target == null) {
                continue;
            }
            Object rawValue = normalizedRow.get(normalizeKey(mapping.getSourceField()));
            if (isBlank(rawValue)) {
                if (mapping.getTransform() == FieldTransform.DEFAULT_VALUE && !isBlank(mapping.getDefaultValue())) {
                    rawValue = mapping.getDefaultValue();
                } else {
                    continue;
                }
            }
            switch (target) {
                case EXTERNAL_ORDER_ID -> externalOrderId = toStringValue(rawValue);
                case ORDER_NUMBER -> orderNumber = toStringValue(rawValue);
                case CUSTOMER_ID -> customerId = toStringValue(rawValue);
                case CUSTOMER_NAME -> customerName = toStringValue(rawValue);
                case CUSTOMER_EMAIL -> customerEmail = toStringValue(rawValue);
                case CURRENCY -> currency = toStringValue(rawValue);
                case ORDER_STATUS -> status = toOrderStatus(rawValue);
                case ORDER_TIME -> orderedAt = toDateTime(rawValue);
                case PAYMENT_METHOD -> paymentMethod = toStringValue(rawValue);
                case PAYMENT_STATUS -> paymentStatus = toStringValue(rawValue);
                case TRANSACTION_ID -> transactionId = toStringValue(rawValue);
                case TOTAL_AMOUNT -> totalAmount = toDecimal(rawValue, target, errors, rowNumber, externalOrderId);
                case SHIPPING_RECIPIENT -> recipient = toStringValue(rawValue);
                case SHIPPING_PHONE -> phone = toStringValue(rawValue);
                case SHIPPING_COUNTRY -> country = toStringValue(rawValue);
                case SHIPPING_PROVINCE -> province = toStringValue(rawValue);
                case SHIPPING_CITY -> city = toStringValue(rawValue);
                case SHIPPING_STREET -> street = toStringValue(rawValue);
                case SHIPPING_ZIPCODE -> zipCode = toStringValue(rawValue);
                case ITEMS -> rawItems = rawValue;
                case ITEM_SKU -> itemSku = toStringValue(rawValue);
                case ITEM_NAME -> itemName = toStringValue(rawValue);
                case ITEM_QUANTITY -> itemQuantity = toInteger(rawValue, target, errors, rowNumber, externalOrderId);
                case ITEM_UNIT_PRICE -> itemUnitPrice =
                        toDecimal(rawValue, target, errors, rowNumber, externalOrderId);
            }
        }

        List<ImportedOrderItem> parsedItems = toItems(rawItems, itemSku, itemName, itemQuantity, itemUnitPrice);
        items.addAll(parsedItems);

        ImportedOrderRow row = new ImportedOrderRow(rowNumber, externalOrderId, orderNumber, customerId,
                customerName, customerEmail, status == null ? OrderStatus.PENDING : status, currency, orderedAt,
                paymentMethod, paymentStatus, transactionId, totalAmount, recipient, phone, country, province,
                city, street, zipCode, items);

        validateRequired(row, errors);
        return new MappingResult(row, errors);
    }

    /**
     * 订单明细解析：优先使用嵌套明细数组，其次退化为「单行单商品」结构
     */
    @SuppressWarnings("unchecked")
    private List<ImportedOrderItem> toItems(Object rawItems, String fallbackSku, String fallbackName,
                                            Integer fallbackQuantity, BigDecimal fallbackUnitPrice) {
        // 文件导入（CSV/JSON）时明细可能以 JSON 数组字符串形式出现，这里做一次兼容解析
        Object candidate = rawItems;
        if (candidate instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                try {
                    candidate = new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, List.class);
                } catch (Exception e) {
                    candidate = null;
                }
            } else {
                candidate = null;
            }
        }
        if (candidate instanceof List<?> list) {
            List<ImportedOrderItem> items = new ArrayList<>();
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> itemRow = normalizeRowKeys((Map<String, Object>) map);
                String sku = firstNonBlank(itemRow, "sku", "skucode", "sellersku", "variantsku", "itemsku");
                String name = firstNonBlank(itemRow, "productname", "itemname", "title", "name");
                int quantity = firstInt(itemRow, 1, "quantity", "qty", "itemquantity");
                BigDecimal unitPrice = firstDecimal(itemRow, "unitprice", "price", "itemprice", "amount");
                items.add(ImportedOrderItem.of(sku, name, quantity, unitPrice));
            }
            return items;
        }
        if (fallbackSku != null || fallbackName != null || fallbackUnitPrice != null) {
            return List.of(ImportedOrderItem.of(fallbackSku, fallbackName,
                    fallbackQuantity == null ? 1 : fallbackQuantity, fallbackUnitPrice));
        }
        return List.of();
    }

    private void validateRequired(ImportedOrderRow row, List<OrderImportError> errors) {
        if (isBlank(row.externalOrderId())) {
            errors.add(OrderImportError.of(row.rowNumber(), null, row.orderNumber(),
                    OrderImportTargetField.EXTERNAL_ORDER_ID.name(), "平台订单号映射值为空，无法确定导入幂等键"));
        }
        if (row.items() == null || row.items().isEmpty()) {
            errors.add(OrderImportError.of(row.rowNumber(), row.externalOrderId(), row.orderNumber(),
                    OrderImportTargetField.ITEMS.name(), "订单明细为空，请检查 ITEMS 或 ITEM_SKU/ITEM_NAME 字段映射"));
        }
        if (isBlank(row.recipient())) {
            errors.add(OrderImportError.of(row.rowNumber(), row.externalOrderId(), row.orderNumber(),
                    OrderImportTargetField.SHIPPING_RECIPIENT.name(), "收件人为空，请检查字段映射配置"));
        }
        if (isBlank(row.street())) {
            errors.add(OrderImportError.of(row.rowNumber(), row.externalOrderId(), row.orderNumber(),
                    OrderImportTargetField.SHIPPING_STREET.name(), "详细地址为空，请检查字段映射配置"));
        }
        if (row.totalAmount() != null && row.totalAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(OrderImportError.of(row.rowNumber(), row.externalOrderId(), row.orderNumber(),
                    OrderImportTargetField.TOTAL_AMOUNT.name(), "订单金额不能为负数: " + row.totalAmount()));
        }
    }

    /**
     * 平台状态文案 -> 系统订单状态，无法识别时回退为 PENDING
     */
    public OrderStatus toOrderStatus(Object rawValue) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        return switch (text.toLowerCase()) {
            case "pending", "unshipped", "new", "created", "waiting", "待处理" -> OrderStatus.PENDING;
            case "processing", "in_progress", "inprogress", "awaiting_shipment", "packing", "处理中" -> OrderStatus.PROCESSING;
            case "confirmed", "confirm", "已确认" -> OrderStatus.CON_FIRMED;
            case "shipped", "delivering", "in_transit", "shipping", "已发货" -> OrderStatus.SHIPPED;
            case "completed", "complete", "delivered", "finished", "done", "已完成" -> OrderStatus.COMPLETED;
            case "cancelled", "canceled", "cancel", "refunded", "已取消" -> OrderStatus.CANCELLED;
            default -> OrderStatus.PENDING;
        };
    }

    /**
     * 多格式时间解析，失败时返回 null（不产生行级错误，下单时间非必填）
     */
    public LocalDateTime toDateTime(Object rawValue) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        // 兼容 ISO 带时区写法（如 2026-01-25T10:20:30Z）
        try {
            return LocalDateTime.parse(text.replace("Z", ""));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String firstNonBlank(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            Object value = row.get(candidate);
            String text = toStringValue(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private int firstInt(Map<String, Object> row, int fallback, String... candidates) {
        for (String candidate : candidates) {
            Object value = row.get(candidate);
            Integer parsed = toInteger(value, null, null, 0, null);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private BigDecimal firstDecimal(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            Object value = row.get(candidate);
            BigDecimal parsed = parseDecimal(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return BigDecimal.ZERO;
    }

    private Map<String, Object> normalizeRowKeys(Map<String, Object> rawRow) {
        Map<String, Object> normalized = new HashMap<>();
        if (rawRow == null) {
            return normalized;
        }
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private String normalizeKey(String key) {
        return key.toLowerCase().replaceAll("[\\s\\-_]", "");
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.isBlank() || "null".equalsIgnoreCase(str.trim()) || "N/A".equalsIgnoreCase(str.trim());
        }
        return false;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private BigDecimal toDecimal(Object rawValue, OrderImportTargetField target, List<OrderImportError> errors,
                                  int rowNumber, String externalOrderId) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null && !isBlank(rawValue)) {
            errors.add(OrderImportError.of(rowNumber, externalOrderId, null, target.name(),
                    "字段值无法转换为数值: " + rawValue));
        }
        return value;
    }

    private Integer toInteger(Object rawValue, OrderImportTargetField target, List<OrderImportError> errors,
                               int rowNumber, String externalOrderId) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null) {
            if (!isBlank(rawValue) && errors != null) {
                errors.add(OrderImportError.of(rowNumber, externalOrderId, null, target.name(),
                        "字段值无法转换为整数: " + rawValue));
            }
            return null;
        }
        return value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal parseDecimal(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof BigDecimal decimal) {
            return decimal;
        }
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            return null;
        }
        // 去掉货币符号、千分位与单位后缀（如 ¥1,299.00）
        String cleaned = text.replaceAll("[^\\d.\\-]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            var matcher = NUMBER_PATTERN.matcher(text);
            if (matcher.find()) {
                try {
                    return new BigDecimal(matcher.group());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
