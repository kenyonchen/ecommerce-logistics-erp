package site.hexaarch.ecommerce.logistics.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductImportError;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportedProductRow;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductAttribute;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 商品字段映射服务：负责平台原始字段与标准商品字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link ProductImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class ProductFieldMappingService {

    public static final String FILE_PLATFORM = "FILE";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final ProductFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的商品行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedProductRow row, List<ProductImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<ProductFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<ProductFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<ProductFieldMapping> enabled = custom.stream()
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
    public List<ProductFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductFieldMapping> mappings = new ArrayList<>();
        List<ImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            ImportTargetField target = ImportTargetField.detectByAlias(sourceField)
                    .or(() -> ImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(ProductFieldMapping.create(null, null, sourceField, target,
                    defaultTransformFor(target), null));
        }
        return mappings;
    }

    /**
     * 某些平台（如重量以千克返回）的默认单位转换
     */
    public FieldTransform defaultTransformFor(ImportTargetField target) {
        return switch (target) {
            case WEIGHT_IN_GRAMS -> FieldTransform.NONE;
            default -> FieldTransform.NONE;
        };
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
     * 将平台原始行转换为标准商品行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<ProductFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<ProductImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String name = null;
        String description = null;
        String categoryName = null;
        String skuCode = null;
        String skuName = null;
        String currency = null;
        BigDecimal sellingPrice = null;
        BigDecimal costPrice = null;
        Integer weightInGrams = null;
        Integer volumeInCubicCentimeters = null;
        Boolean active = null;
        List<ProductAttribute> attributes = new ArrayList<>();
        String externalProductId = null;
        String externalSkuId = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (ProductFieldMapping mapping : mappings) {
            ImportTargetField target = mapping.getTargetField();
            if (target == null) {
                continue;
            }
            String sourceField = mapping.getSourceField();
            Object rawValue = normalizedRow.get(normalizeKey(sourceField));
            if (isBlank(rawValue)) {
                if (mapping.getTransform() == FieldTransform.DEFAULT_VALUE && !isBlank(mapping.getDefaultValue())) {
                    rawValue = mapping.getDefaultValue();
                } else {
                    continue;
                }
            }
            FieldTransform transform = mapping.getTransform() == null ? FieldTransform.NONE : mapping.getTransform();

            switch (target) {
                case PRODUCT_NAME -> name = toStringValue(rawValue);
                case PRODUCT_DESCRIPTION -> description = toStringValue(rawValue);
                case CATEGORY_NAME -> categoryName = toStringValue(rawValue);
                case SKU_CODE -> skuCode = toStringValue(rawValue);
                case SKU_NAME -> skuName = toStringValue(rawValue);
                case CURRENCY -> currency = toStringValue(rawValue);
                case EXTERNAL_PRODUCT_ID -> externalProductId = toStringValue(rawValue);
                case EXTERNAL_SKU_ID -> externalSkuId = toStringValue(rawValue);
                case SELLING_PRICE -> sellingPrice = toDecimal(rawValue, target, errors, rowNumber, rawRow);
                case COST_PRICE -> costPrice = toDecimal(rawValue, target, errors, rowNumber, rawRow);
                case WEIGHT_IN_GRAMS -> weightInGrams = toInteger(rawValue, target, transform, errors, rowNumber, rawRow);
                case VOLUME_IN_CC -> volumeInCubicCentimeters =
                        toInteger(rawValue, target, FieldTransform.NONE, errors, rowNumber, rawRow);
                case ACTIVE -> active = toBoolean(rawValue);
                case ATTRIBUTES -> attributes.addAll(toAttributes(rawValue));
            }
        }

        if (active == null) {
            active = Boolean.TRUE;
        }

        ImportedProductRow row = new ImportedProductRow(rowNumber, externalProductId, externalSkuId, name, description,
                categoryName, skuCode, skuName, sellingPrice, costPrice, currency, weightInGrams,
                volumeInCubicCentimeters, attributes, active);

        validateRequired(row, errors, rawRow);
        return new MappingResult(row, errors);
    }

    private void validateRequired(ImportedProductRow row, List<ProductImportError> errors, Map<String, Object> rawRow) {
        if (isBlank(row.name())) {
            errors.add(ProductImportError.of(row.rowNumber(), row.externalProductId(), row.skuCode(),
                    ImportTargetField.PRODUCT_NAME.name(), "商品名称映射值为空，请检查字段映射配置"));
        }
        if (isBlank(row.skuCode())) {
            errors.add(ProductImportError.of(row.rowNumber(), row.externalProductId(), null,
                    ImportTargetField.SKU_CODE.name(), "SKU编码映射值为空，请检查字段映射配置"));
        }
        if (row.sellingPrice() != null && row.sellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(ProductImportError.of(row.rowNumber(), row.externalProductId(), row.skuCode(),
                    ImportTargetField.SELLING_PRICE.name(), "销售价不能为负数: " + row.sellingPrice()));
        }
        if (row.weightInGrams() != null && row.weightInGrams() < 0) {
            errors.add(ProductImportError.of(row.rowNumber(), row.externalProductId(), row.skuCode(),
                    ImportTargetField.WEIGHT_IN_GRAMS.name(), "重量不能为负数: " + row.weightInGrams()));
        }
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

    private BigDecimal toDecimal(Object rawValue, ImportTargetField target, List<ProductImportError> errors,
                                 int rowNumber, Map<String, Object> rawRow) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null && !isBlank(rawValue)) {
            errors.add(ProductImportError.of(rowNumber, extractExternalId(rawRow), extractSkuCode(rawRow),
                    target.name(), "字段值无法转换为数值: " + rawValue));
        }
        return value;
    }

    private Integer toInteger(Object rawValue, ImportTargetField target, FieldTransform transform,
                              List<ProductImportError> errors, int rowNumber, Map<String, Object> rawRow) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null) {
            if (!isBlank(rawValue)) {
                errors.add(ProductImportError.of(rowNumber, extractExternalId(rawRow), extractSkuCode(rawRow),
                        target.name(), "字段值无法转换为数值: " + rawValue));
            }
            return null;
        }
        if (transform == FieldTransform.WEIGHT_KG_TO_G) {
            value = value.multiply(BigDecimal.valueOf(1000L));
        }
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
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
        // 去掉货币符号、千分位与单位后缀（如 ¥1,299.00、0.25kg）
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

    private Boolean toBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(rawValue).trim().toLowerCase();
        return switch (text) {
            case "true", "1", "y", "yes", "是", "active", "enabled", "启用", "上架", "on" -> Boolean.TRUE;
            case "false", "0", "n", "no", "否", "inactive", "disabled", "停用", "下架", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private List<ProductAttribute> toAttributes(Object rawValue) {
        List<ProductAttribute> attributes = new ArrayList<>();
        if (rawValue == null) {
            return attributes;
        }
        if (rawValue instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                attributes.add(ProductAttribute.builder()
                        .name(String.valueOf(entry.getKey()).trim())
                        .value(entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim())
                        .build());
            }
            return attributes;
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            return attributes;
        }
        if (text.startsWith("{") || text.startsWith("[")) {
            attributes.addAll(toAttributes(parseJsonAttributes(text)));
            return attributes;
        }
        String[] pairs = text.split("[;；,、|]");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("[:：=]", 2);
            if (kv.length == 2) {
                attributes.add(ProductAttribute.builder()
                        .name(kv[0].trim())
                        .value(kv[1].trim())
                        .build());
            } else {
                attributes.add(ProductAttribute.builder().name(pair.trim()).value(pair.trim()).build());
            }
        }
        return attributes;
    }

    /**
     * 简单的JSON对象解析，避免引入额外依赖；解析失败时退化为整体作为一个属性。
     */
    private Map<String, Object> parseJsonAttributes(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = text.substring(1, text.length() - 1).trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        String[] entries = trimmed.split(",");
        for (String entry : entries) {
            String[] kv = entry.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replace("\"", "").replace("{", "");
            String value = kv[1].trim().replace("\"", "").replace("}", "");
            result.put(key, value);
        }
        return result;
    }

    private String extractExternalId(Map<String, Object> rawRow) {
        if (rawRow == null) {
            return null;
        }
        Optional<String> candidate = ImportTargetField.detectByAlias("externalProductId")
                .flatMap(field -> findColumn(rawRow, field));
        return candidate.orElse(null);
    }

    private String extractSkuCode(Map<String, Object> rawRow) {
        if (rawRow == null) {
            return null;
        }
        return ImportTargetField.detectByAlias("sku")
                .flatMap(field -> findColumn(rawRow, field))
                .orElse(null);
    }

    private Optional<String> findColumn(Map<String, Object> rawRow, ImportTargetField field) {
        for (String alias : field.getAliases()) {
            String normalizedAlias = normalizeKey(alias);
            for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (normalizedAlias.equals(normalizeKey(entry.getKey())) && entry.getValue() != null) {
                    return Optional.of(String.valueOf(entry.getValue()));
                }
            }
        }
        return Optional.empty();
    }
}
