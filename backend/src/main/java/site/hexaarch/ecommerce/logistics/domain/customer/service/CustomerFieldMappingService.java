package site.hexaarch.ecommerce.logistics.domain.customer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerImportError;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.ImportedCustomerRow;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户字段映射服务：负责平台原始买家字段与标准客户字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link CustomerImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class CustomerFieldMappingService {

    private final CustomerFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的客户行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedCustomerRow row, List<CustomerImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<CustomerFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<CustomerFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<CustomerFieldMapping> enabled = custom.stream()
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
    public List<CustomerFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<CustomerFieldMapping> mappings = new ArrayList<>();
        List<CustomerImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            CustomerImportTargetField target = CustomerImportTargetField.detectByAlias(sourceField)
                    .or(() -> CustomerImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(CustomerFieldMapping.create(null, null, sourceField, target, FieldTransform.NONE, null));
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
     * 将平台原始行转换为标准客户行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<CustomerFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<CustomerImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String externalBuyerId = null;
        String customerName = null;
        String email = null;
        String phone = null;
        String company = null;
        String country = null;
        String province = null;
        String city = null;
        String address = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (CustomerFieldMapping mapping : mappings) {
            CustomerImportTargetField target = mapping.getTargetField();
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
                case EXTERNAL_BUYER_ID -> externalBuyerId = toStringValue(rawValue);
                case CUSTOMER_NAME -> customerName = toStringValue(rawValue);
                case CUSTOMER_EMAIL -> email = toStringValue(rawValue);
                case CUSTOMER_PHONE -> phone = toStringValue(rawValue);
                case COMPANY -> company = toStringValue(rawValue);
                case COUNTRY -> country = toStringValue(rawValue);
                case PROVINCE -> province = toStringValue(rawValue);
                case CITY -> city = toStringValue(rawValue);
                case ADDRESS -> address = toStringValue(rawValue);
            }
        }

        ImportedCustomerRow row = new ImportedCustomerRow(rowNumber, externalBuyerId, customerName, email,
                phone, company, country, province, city, address);

        validateRequired(row, errors);
        return new MappingResult(row, errors);
    }

    private void validateRequired(ImportedCustomerRow row, List<CustomerImportError> errors) {
        if (isBlank(row.customerName())) {
            errors.add(CustomerImportError.of(row.rowNumber(), row.externalBuyerId(), null,
                    CustomerImportTargetField.CUSTOMER_NAME.name(), "客户名称映射值为空，请检查字段映射配置"));
        }
        if (!isBlank(row.email()) && !row.email().contains("@")) {
            errors.add(CustomerImportError.of(row.rowNumber(), row.externalBuyerId(), row.customerName(),
                    CustomerImportTargetField.CUSTOMER_EMAIL.name(), "邮箱格式不正确: " + row.email()));
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
}
