package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shopify 响应体的解析工具：解包 {@code {"orders":[...]}} 这类包装、按平台字段取类型安全的值。
 *
 * @author kenyon
 */
final class ShopifyJson {

    /**
     * Shopify 时间戳是带时区偏移的 ISO8601（如 {@code 2026-01-25T12:00:00-04:00}）。
     * 导入侧的日期解析器只认 {@code yyyy-MM-dd HH:mm:ss} 等形态，所以统一在此换算成 UTC 墙上时间
     * （与 {@code spring.datasource.url} 的 {@code serverTimezone=UTC} 口径一致）。
     */
    private static final DateTimeFormatter IMPORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShopifyJson() {
    }

    static List<Map<String, Object>> collection(String jsonBody, String wrapperKey) {
        Object parsed = read(jsonBody);
        Object candidate = parsed;
        if (parsed instanceof Map<?, ?> map) {
            candidate = map.get(wrapperKey);
        }
        if (!(candidate instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
                }));
            }
        }
        return rows;
    }

    static Map<String, Object> object(String jsonBody) {
        Object parsed = read(jsonBody);
        if (parsed instanceof Map<?, ?> map) {
            return MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
            });
        }
        throw new BusinessException("Shopify 响应结构不是预期对象: " + truncate(jsonBody), "SHOPIFY_RESPONSE_INVALID");
    }

    /**
     * 取 Shopify 单资源接口的包装对象，如 {@code {"order": {...}}}
     */
    static Map<String, Object> wrappedObject(String jsonBody, String wrapperKey) {
        Map<String, Object> root = object(jsonBody);
        Object wrapped = root.get(wrapperKey);
        if (wrapped instanceof Map<?, ?> map) {
            return MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
            });
        }
        return root;
    }

    static Map<String, Object> bodyOf(ResponseEntity<String> response) {
        return object(response.getBody());
    }

    /**
     * 点号路径取值：{@code path("shipping_address", "address1")}
     */
    @SuppressWarnings("unchecked")
    static Object path(Map<String, Object> node, String... keys) {
        Object current = node;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }

    static String text(Map<String, Object> node, String... keys) {
        Object value = path(node, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 数字/布尔取字符串形式（平台订单号是 Long，导入侧按字符串处理）
     */
    static String idText(Map<String, Object> node, String... keys) {
        Object value = path(node, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 取整数字段；缺失或无法解析返回 {@code null}，让调用方区分「没有这个字段」和「值为 0」。
     */
    static Integer integer(Map<String, Object> node, String... keys) {
        Object value = path(node, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) MAPPER.convertValue(map, Map.class));
                }
            }
            return rows;
        }
        return Collections.emptyList();
    }

    static String toImportDateTime(String isoTimestamp) {
        LocalDateTime value = toUtcLocalDateTime(isoTimestamp);
        return value == null ? null : value.format(IMPORT_FORMATTER);
    }

    /**
     * 同 {@link #toImportDateTime}，但保留 {@link LocalDateTime} 形态给需要类型化时间戳的调用方。
     */
    static LocalDateTime toUtcLocalDateTime(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }
        String text = isoTimestamp.trim();
        try {
            return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            // 兼容不带偏移的写法，按 UTC 处理
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            // 再退到 epoch
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (Exception e) {
            throw new BusinessException("无法解析 Shopify 时间字段: " + text, "SHOPIFY_TIMESTAMP_INVALID");
        }
    }

    /**
     * Shopify 官方格式（ISO8601 + UTC）用于 {@code updated_at_min} 这类查询参数。
     */
    static String toQueryTimestamp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static Map<String, Object> linkedRow() {
        return new LinkedHashMap<>();
    }


    private static Object read(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(jsonBody, Object.class);
        } catch (Exception e) {
            throw new BusinessException("Shopify 响应不是合法 JSON: " + truncate(jsonBody), "SHOPIFY_RESPONSE_INVALID");
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 200) + "...";
    }
}
