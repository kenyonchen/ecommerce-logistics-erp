package site.hexaarch.ecommerce.logistics.domain.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceImportError;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.FinanceImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.ImportedFinanceRow;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
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
 * 财务字段映射服务：负责平台原始字段与标准财务字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link FinanceImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class FinanceFieldMappingService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Za-z]{3}");
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

    private final FinanceFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的财务流水行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedFinanceRow row, List<FinanceImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<FinanceFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<FinanceFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<FinanceFieldMapping> enabled = custom.stream()
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
    public List<FinanceFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<FinanceFieldMapping> mappings = new ArrayList<>();
        List<FinanceImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            FinanceImportTargetField target = FinanceImportTargetField.detectByAlias(sourceField)
                    .or(() -> FinanceImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(FinanceFieldMapping.create(null, null, sourceField, target, FieldTransform.NONE, null));
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
     * 将平台原始行转换为标准财务流水行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<FinanceFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<FinanceImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String externalTransactionId = null;
        TransactionType type = null;
        BigDecimal amount = null;
        String currency = null;
        String referenceId = null;
        String referenceType = null;
        String description = null;
        TransactionStatus status = null;
        LocalDateTime occurredAt = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (FinanceFieldMapping mapping : mappings) {
            FinanceImportTargetField target = mapping.getTargetField();
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
                case EXTERNAL_TRANSACTION_ID -> externalTransactionId = toStringValue(rawValue);
                case TYPE -> type = toTransactionType(rawValue, errors, rowNumber, externalTransactionId);
                case AMOUNT -> amount = toDecimal(rawValue, target, errors, rowNumber, externalTransactionId);
                case CURRENCY -> currency = toCurrency(rawValue, target, errors, rowNumber, externalTransactionId);
                case REFERENCE_ID -> referenceId = toStringValue(rawValue);
                case REFERENCE_TYPE -> referenceType = toStringValue(rawValue);
                case DESCRIPTION -> description = toStringValue(rawValue);
                case STATUS -> status = toTransactionStatus(rawValue);
                case OCCURRED_AT -> occurredAt = toDateTime(rawValue);
            }
        }

        ImportedFinanceRow row = new ImportedFinanceRow(rowNumber, externalTransactionId, type, amount, currency,
                referenceId, referenceType, description,
                status == null ? TransactionStatus.SUCCESS : status, occurredAt);

        validateRequired(row, errors);
        return new MappingResult(row, errors);
    }

    private void validateRequired(ImportedFinanceRow row, List<FinanceImportError> errors) {
        if (isBlank(row.externalTransactionId())) {
            errors.add(FinanceImportError.of(row.rowNumber(), null,
                    FinanceImportTargetField.EXTERNAL_TRANSACTION_ID.name(),
                    "平台交易流水号映射值为空，无法建立导入幂等键"));
        }
        if (row.type() == null) {
            errors.add(FinanceImportError.of(row.rowNumber(), row.externalTransactionId(),
                    FinanceImportTargetField.TYPE.name(), "交易类型为空或无法识别，请检查字段映射配置"));
        }
        if (row.amount() == null) {
            errors.add(FinanceImportError.of(row.rowNumber(), row.externalTransactionId(),
                    FinanceImportTargetField.AMOUNT.name(), "金额为空，请检查字段映射配置"));
        }
        if (isBlank(row.currency())) {
            errors.add(FinanceImportError.of(row.rowNumber(), row.externalTransactionId(),
                    FinanceImportTargetField.CURRENCY.name(), "币种为空，请检查字段映射配置"));
        }
    }

    /**
     * 平台交易类型文案 -> 系统交易类型；无法识别时返回 null 并产生行级错误
     */
    public TransactionType toTransactionType(Object rawValue, List<FinanceImportError> errors,
                                             int rowNumber, String externalTransactionId) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        // 兼容直接给出系统枚举名的场景
        try {
            return TransactionType.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // 继续走平台词转换规则
        }
        TransactionType mapped = switch (text.toLowerCase()) {
            case "income", "payout", "deposit", "sale", "sales", "settlement", "payment", "payment_received",
                    "reimbursement", "收入", "回款", "入账", "结算", "打款", "销售" -> TransactionType.INCOME;
            case "expense", "fee", "commission", "cost", "charge", "shipping_fee", "advertizing", "advertising",
                    "支出", "费用", "手续费", "佣金", "扣款" -> TransactionType.EXPENSE;
            case "refund", "return", "chargeback", "退款", "退货" -> TransactionType.REFUND;
            case "adjustment", "correction", "reconciliation", "transfer", "调整" -> TransactionType.ADJUSTMENT;
            default -> null;
        };
        if (mapped == null && errors != null) {
            errors.add(FinanceImportError.of(rowNumber, externalTransactionId,
                    FinanceImportTargetField.TYPE.name(), "无法识别的交易类型: " + text));
        }
        return mapped;
    }

    /**
     * 平台状态文案 -> 系统交易状态；无法识别或缺失时回退为 SUCCESS（缺省成功）
     */
    public TransactionStatus toTransactionStatus(Object rawValue) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        try {
            return TransactionStatus.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // 继续走平台词转换规则
        }
        return switch (text.toLowerCase()) {
            case "pending", "processing", "in_progress", "scheduled", "deferred", "待处理", "处理中" ->
                    TransactionStatus.PENDING;
            case "success", "succeeded", "completed", "complete", "paid", "posted", "settled", "available",
                    "成功", "已到账" -> TransactionStatus.SUCCESS;
            case "failed", "failure", "error", "declined", "rejected", "denied", "失败" -> TransactionStatus.FAILED;
            case "cancelled", "canceled", "cancel", "voided", "no longer available", "已取消" ->
                    TransactionStatus.CANCELLED;
            default -> TransactionStatus.SUCCESS;
        };
    }

    /**
     * 币种校验：仅接受 ISO 三字母码，统一转为大写
     */
    private String toCurrency(Object rawValue, FinanceImportTargetField target, List<FinanceImportError> errors,
                              int rowNumber, String externalTransactionId) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        String normalized = text.toUpperCase();
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            errors.add(FinanceImportError.of(rowNumber, externalTransactionId, target.name(),
                    "币种不是合法的ISO三字母码: " + text));
            return null;
        }
        return normalized;
    }

    /**
     * 多格式时间解析，失败时返回 null（不产生行级错误，发生时间非必填）
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

    private BigDecimal toDecimal(Object rawValue, FinanceImportTargetField target, List<FinanceImportError> errors,
                                 int rowNumber, String externalTransactionId) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null && !isBlank(rawValue)) {
            errors.add(FinanceImportError.of(rowNumber, externalTransactionId, target.name(),
                    "字段值无法转换为数值: " + rawValue));
        }
        return value;
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
