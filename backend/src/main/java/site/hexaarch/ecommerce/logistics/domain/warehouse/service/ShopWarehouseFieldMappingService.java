package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseImportError;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ImportedShopWarehouseRow;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ShopWarehouseImportTargetField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 店铺仓库字段映射服务：负责平台原始字段与标准店铺仓库字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link ShopWarehouseImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class ShopWarehouseFieldMappingService {

    public static final String FILE_PLATFORM = "FILE";

    private final ShopWarehouseFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的店铺仓库行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedShopWarehouseRow row, List<ShopWarehouseImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<ShopWarehouseFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<ShopWarehouseFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<ShopWarehouseFieldMapping> enabled = custom.stream()
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
    public List<ShopWarehouseFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShopWarehouseFieldMapping> mappings = new ArrayList<>();
        List<ShopWarehouseImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            ShopWarehouseImportTargetField target = ShopWarehouseImportTargetField.detectByAlias(sourceField)
                    .or(() -> ShopWarehouseImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(ShopWarehouseFieldMapping.create(null, null, sourceField, target, FieldTransform.NONE, null));
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
     * 将平台原始行转换为标准店铺仓库行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<ShopWarehouseFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<ShopWarehouseImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String shopCode = null;
        String shopName = null;
        String platform = null;
        String platformWarehouseId = null;
        String platformWarehouseName = null;
        String warehouseCode = null;
        String address = null;
        Boolean isDefault = null;
        Boolean active = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (ShopWarehouseFieldMapping mapping : mappings) {
            ShopWarehouseImportTargetField target = mapping.getTargetField();
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

            switch (target) {
                case SHOP_CODE -> shopCode = toStringValue(rawValue);
                case SHOP_NAME -> shopName = toStringValue(rawValue);
                case PLATFORM -> platform = toStringValue(rawValue);
                case PLATFORM_WAREHOUSE_ID -> platformWarehouseId = toStringValue(rawValue);
                case PLATFORM_WAREHOUSE_NAME -> platformWarehouseName = toStringValue(rawValue);
                case WAREHOUSE_CODE -> warehouseCode = toStringValue(rawValue);
                case ADDRESS -> address = toStringValue(rawValue);
                case IS_DEFAULT -> isDefault = toBoolean(rawValue);
                case ACTIVE -> active = toBoolean(rawValue);
            }
        }

        if (active == null) {
            active = Boolean.TRUE;
        }

        ImportedShopWarehouseRow row = new ImportedShopWarehouseRow(rowNumber, shopCode, shopName, platform,
                platformWarehouseId, platformWarehouseName, warehouseCode, address, isDefault, active);

        validateRequired(row, errors);
        return new MappingResult(row, errors);
    }

    private void validateRequired(ImportedShopWarehouseRow row, List<ShopWarehouseImportError> errors) {
        if (isBlank(row.platformWarehouseId())) {
            errors.add(ShopWarehouseImportError.of(row.rowNumber(), row.shopCode(), null,
                    ShopWarehouseImportTargetField.PLATFORM_WAREHOUSE_ID.name(),
                    "平台仓库ID映射值为空，请检查字段映射配置"));
        }
        if (isBlank(row.warehouseCode())) {
            errors.add(ShopWarehouseImportError.of(row.rowNumber(), row.shopCode(), row.platformWarehouseId(),
                    ShopWarehouseImportTargetField.WAREHOUSE_CODE.name(),
                    "本地仓库编码映射值为空，请检查字段映射配置"));
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

    private Boolean toBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(rawValue).trim().toLowerCase();
        return switch (text) {
            case "true", "1", "y", "yes", "是", "active", "enabled", "启用", "on", "default" -> Boolean.TRUE;
            case "false", "0", "n", "no", "否", "inactive", "disabled", "停用", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * 从原始行中提取指定目标字段的值（用于错误明细回显）
     */
    public Optional<String> extractValue(Map<String, Object> rawRow, ShopWarehouseImportTargetField field) {
        if (rawRow == null) {
            return Optional.empty();
        }
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
