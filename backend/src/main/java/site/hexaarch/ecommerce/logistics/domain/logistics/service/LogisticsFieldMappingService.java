package site.hexaarch.ecommerce.logistics.domain.logistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsImportError;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.ImportedLogisticsRow;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;
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
 * 物流字段映射服务：负责平台原始物流字段与标准物流字段之间的识别、转换与行级校验。
 * <p>
 * 行级转换不会抛出异常，所有问题以 {@link LogisticsImportError} 形式返回，保证「一行失败不影响其他行」。
 * </p>
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class LogisticsFieldMappingService {

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

    private final LogisticsFieldMappingRepository fieldMappingRepository;

    /**
     * 单次行转换的结果
     *
     * @param row    标准化后的物流行（即使存在字段级错误也会尽量给出部分结果）
     * @param errors 字段级错误明细
     */
    public record MappingResult(ImportedLogisticsRow row, List<LogisticsImportError> errors) {
    }

    /**
     * 解析映射配置：优先使用租户自定义映射，否则基于表头自动推断。
     */
    public List<LogisticsFieldMapping> resolveMappings(String tenantId, String platform, List<String> sourceFields) {
        List<LogisticsFieldMapping> custom = fieldMappingRepository.findByTenantIdAndPlatform(tenantId, platform);
        if (custom != null) {
            List<LogisticsFieldMapping> enabled = custom.stream()
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
    public List<LogisticsFieldMapping> autoDetect(List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<LogisticsFieldMapping> mappings = new ArrayList<>();
        List<LogisticsImportTargetField> usedTargets = new ArrayList<>();
        for (String sourceField : sourceFields) {
            LogisticsImportTargetField target = LogisticsImportTargetField.detectByAlias(sourceField)
                    .or(() -> LogisticsImportTargetField.detectFuzzy(sourceField))
                    .orElse(null);
            if (target == null || usedTargets.contains(target)) {
                continue;
            }
            usedTargets.add(target);
            mappings.add(LogisticsFieldMapping.create(null, null, sourceField, target, FieldTransform.NONE, null));
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
     * 将平台原始行转换为标准物流行（行内错误不抛出，收集返回）
     */
    public MappingResult mapRow(List<LogisticsFieldMapping> mappings, Map<String, Object> rawRow, int rowNumber) {
        List<LogisticsImportError> errors = new ArrayList<>();
        Map<String, Object> normalizedRow = normalizeRowKeys(rawRow);

        String externalLogisticsId = null;
        String trackingNumber = null;
        String orderNumber = null;
        String externalOrderId = null;
        String channelCode = null;
        String carrier = null;
        LogisticsStatus status = null;
        String trackingUrl = null;
        String labelUrl = null;
        String labelFormat = null;
        String barcode = null;
        LocalDateTime shippedAt = null;
        LocalDateTime estimatedArrivalAt = null;
        LocalDateTime deliveredAt = null;
        BigDecimal totalFee = null;
        String currency = null;
        String recipient = null;
        String phone = null;
        String country = null;
        String province = null;
        String city = null;
        String street = null;
        String zipCode = null;

        Object rawTraces = null;
        LocalDateTime traceTime = null;
        String traceStatus = null;
        String traceDescription = null;
        String traceLocation = null;

        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        for (LogisticsFieldMapping mapping : mappings) {
            LogisticsImportTargetField target = mapping.getTargetField();
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
                case EXTERNAL_LOGISTICS_ID -> externalLogisticsId = toStringValue(rawValue);
                case TRACKING_NUMBER -> trackingNumber = toStringValue(rawValue);
                case ORDER_NUMBER -> orderNumber = toStringValue(rawValue);
                case EXTERNAL_ORDER_ID -> externalOrderId = toStringValue(rawValue);
                case CHANNEL_CODE -> channelCode = toStringValue(rawValue);
                case CARRIER -> carrier = toStringValue(rawValue);
                case STATUS -> status = toLogisticsStatus(rawValue);
                case TRACKING_URL -> trackingUrl = toStringValue(rawValue);
                case LABEL_URL -> labelUrl = toStringValue(rawValue);
                case LABEL_FORMAT -> labelFormat = toStringValue(rawValue);
                case BARCODE -> barcode = toStringValue(rawValue);
                case SHIPPED_AT -> shippedAt = toDateTime(rawValue);
                case ESTIMATED_ARRIVAL_AT -> estimatedArrivalAt = toDateTime(rawValue);
                case DELIVERED_AT -> deliveredAt = toDateTime(rawValue);
                case TOTAL_FEE -> totalFee = toDecimal(rawValue, target, errors, rowNumber, externalLogisticsId);
                case CURRENCY -> currency = toStringValue(rawValue);
                case RECIPIENT -> recipient = toStringValue(rawValue);
                case PHONE -> phone = toStringValue(rawValue);
                case COUNTRY -> country = toStringValue(rawValue);
                case PROVINCE -> province = toStringValue(rawValue);
                case CITY -> city = toStringValue(rawValue);
                case STREET -> street = toStringValue(rawValue);
                case ZIP_CODE -> zipCode = toStringValue(rawValue);
                case TRACES -> rawTraces = rawValue;
                case TRACE_TIME -> traceTime = toDateTime(rawValue);
                case TRACE_STATUS -> traceStatus = toStringValue(rawValue);
                case TRACE_DESCRIPTION -> traceDescription = toStringValue(rawValue);
                case TRACE_LOCATION -> traceLocation = toStringValue(rawValue);
            }
        }

        List<LogisticsTrace> traces = toTraces(rawTraces, traceTime, traceStatus, traceDescription, traceLocation);
        if (status == null && !traces.isEmpty()) {
            // 平台未回传单状态时，以最后一条轨迹节点的时间/描述作为推进依据
            LogisticsTrace last = traces.get(traces.size() - 1);
            status = toLogisticsStatus(last.status());
        }

        ImportedLogisticsRow row = new ImportedLogisticsRow(rowNumber, externalLogisticsId, trackingNumber,
                orderNumber, externalOrderId, channelCode, carrier,
                status == null ? LogisticsStatus.PENDING : status, trackingUrl, labelUrl, labelFormat, barcode,
                shippedAt, estimatedArrivalAt, deliveredAt, totalFee, currency, recipient, phone, country, province,
                city, street, zipCode, traces);

        validateRequired(row, errors);
        return new MappingResult(row, errors);
    }

    /**
     * 轨迹解析：优先使用嵌套轨迹数组（也兼容 JSON 字符串写法），其次退化为「单行单轨迹」结构
     */
    @SuppressWarnings("unchecked")
    private List<LogisticsTrace> toTraces(Object rawTraces, LocalDateTime fallbackTime, String fallbackStatus,
                                          String fallbackDescription, String fallbackLocation) {
        Object candidate = rawTraces;
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
        List<LogisticsTrace> traces = new ArrayList<>();
        if (candidate instanceof List<?> list) {
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> traceRow = normalizeRowKeys((Map<String, Object>) map);
                LocalDateTime time = firstDateTime(traceRow, "time", "traceTime", "acceptTime", "createTime",
                        "operationTime");
                String status = firstNonBlank(traceRow, "status", "traceStatus", "logisticsStatus", "action");
                String description = firstNonBlank(traceRow, "description", "desc", "remark", "content",
                        "acceptAddress");
                String location = firstNonBlank(traceRow, "location", "address", "city", "site");
                if (time == null && description == null) {
                    continue;
                }
                traces.add(LogisticsTrace.of(time, status, description, location));
            }
            return traces;
        }
        if (fallbackDescription != null || fallbackTime != null) {
            traces.add(LogisticsTrace.of(fallbackTime, fallbackStatus, fallbackDescription, fallbackLocation));
        }
        return traces;
    }

    private void validateRequired(ImportedLogisticsRow row, List<LogisticsImportError> errors) {
        if (isBlank(row.externalLogisticsId())) {
            errors.add(LogisticsImportError.of(row.rowNumber(), null, row.orderNumber(),
                    LogisticsImportTargetField.EXTERNAL_LOGISTICS_ID.name(),
                    "平台物流单号为空，无法确定导入幂等键"));
        }
        if (isBlank(row.orderNumber()) && isBlank(row.externalOrderId())) {
            errors.add(LogisticsImportError.of(row.rowNumber(), row.externalLogisticsId(), null,
                    LogisticsImportTargetField.EXTERNAL_ORDER_ID.name(),
                    "缺少订单标识（订单号或平台订单号），物流单无法关联到系统订单"));
        }
        if (row.totalFee() != null && row.totalFee().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(LogisticsImportError.of(row.rowNumber(), row.externalLogisticsId(), row.orderNumber(),
                    LogisticsImportTargetField.TOTAL_FEE.name(), "物流费用不能为负数: " + row.totalFee()));
        }
    }

    /**
     * 平台物流状态文案 -> 系统物流状态，无法识别时回退为 PENDING
     */
    public LogisticsStatus toLogisticsStatus(Object rawValue) {
        String text = toStringValue(rawValue);
        if (text == null) {
            return null;
        }
        return switch (text.toLowerCase()) {
            case "pending", "created", "new", "waiting", "unshipped", "待处理", "待发货" -> LogisticsStatus.PENDING;
            case "collected", "pickedup", "picked_up", "accepted", "揽收", "已揽收", "已收货" ->
                    LogisticsStatus.COLLECTED;
            case "labelgenerated", "label_generated", "printed", "generated", "已打单", "已面单" ->
                    LogisticsStatus.LABEL_GENERATED;
            case "shipped", "intransit", "in_transit", "transit", "transporting", "moving", "运输中", "已发货",
                    "在途" -> LogisticsStatus.IN_TRANSIT;
            case "delivering", "out_for_delivery", "outforDelivery", "sending", "派送中", "配送中" ->
                    LogisticsStatus.DELIVERING;
            case "delivered", "signed", "completed", "finish", "已签收", "已送达", "签收" -> LogisticsStatus.DELIVERED;
            case "failed", "exception", "abnormal", "return", "returned", "配送失败", "异常", "退回" ->
                    LogisticsStatus.FAILED;
            case "cancelled", "canceled", "cancel", "closed", "已取消", "关闭" -> LogisticsStatus.CANCELLED;
            default -> {
                try {
                    yield LogisticsStatus.valueOf(text.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    yield LogisticsStatus.PENDING;
                }
            }
        };
    }

    /**
     * 多格式时间解析，失败时返回 null（不产生行级错误，时间为非必填）
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

    private LocalDateTime firstDateTime(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            LocalDateTime value = toDateTime(row.get(candidate));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            String text = toStringValue(row.get(candidate));
            if (text != null) {
                return text;
            }
        }
        return null;
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
        return result.isEmpty() || "null".equalsIgnoreCase(result) ? null : result;
    }

    private BigDecimal toDecimal(Object rawValue, LogisticsImportTargetField target,
                                List<LogisticsImportError> errors, int rowNumber, String externalLogisticsId) {
        BigDecimal value = parseDecimal(rawValue);
        if (value == null && !isBlank(rawValue)) {
            errors.add(LogisticsImportError.of(rowNumber, externalLogisticsId, null, target.name(),
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
        // 去掉货币符号、千分位与单位后缀（如 ¥12.50）
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
