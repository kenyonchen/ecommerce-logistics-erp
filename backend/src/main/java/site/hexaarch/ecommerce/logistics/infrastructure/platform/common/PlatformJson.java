package site.hexaarch.ecommerce.logistics.infrastructure.platform.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 平台响应体的通用解析工具：解包 {@code {"orders":[...]}} 这类包装、按点号路径取类型安全的值。
 * <p>
 * 各家的响应信封差别很大（Shopify 是 {@code {"orders":[…]}}、TOP 是 {@code {xxx_response:{…}}}、
 * Ozon 是 {@code {result:[…]}}、TikTok 是 {@code {data:{items:[…]}}}），但「取集合、按路径取标量、
 * 把时间归一到 UTC」这三件事是同构的，所以收敛到一处而不是每个适配器复制一遍。
 *
 * @author kenyon
 */
public final class PlatformJson {

    /**
     * 导入侧的日期解析器只认 {@code yyyy-MM-dd HH:mm:ss} 等形态，所以统一换算成 UTC 墙上时间
     * （与 {@code spring.datasource.url} 的 {@code serverTimezone=UTC} 口径一致）。
     */
    private static final DateTimeFormatter IMPORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlatformJson() {
    }

    /**
     * 取包装对象里的数组字段；根节点本身就是数组时直接取根。
     */
    public static List<Map<String, Object>> collection(String jsonBody, String wrapperKey) {
        Object parsed = read(jsonBody);
        Object candidate = parsed;
        if (parsed instanceof Map<?, ?> map) {
            candidate = map.get(wrapperKey);
            if (candidate == null) {
                // 支持一层嵌套信封，如 {"data":{"items":[…]}} / {"search_result":{"items":[…]}}
                for (Object value : map.values()) {
                    if (value instanceof Map<?, ?> inner) {
                        candidate = inner.get(wrapperKey);
                        if (candidate != null) {
                            break;
                        }
                    }
                }
            }
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

    public static Map<String, Object> object(String jsonBody) {
        Object parsed = read(jsonBody);
        if (parsed instanceof Map<?, ?> map) {
            return MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
            });
        }
        throw new BusinessException("平台响应结构不是预期对象: " + truncate(jsonBody), "PLATFORM_RESPONSE_INVALID");
    }

    /**
     * 取单资源接口的包装对象，如 {@code {"order": {...}}}；包装键不存在时退回根节点
     * （Ozon、Magento 的部分接口直接返回裸对象）。
     */
    public static Map<String, Object> wrappedObject(String jsonBody, String wrapperKey) {
        Map<String, Object> root = object(jsonBody);
        Object wrapped = root.get(wrapperKey);
        if (wrapped instanceof Map<?, ?> map) {
            return MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
            });
        }
        return root;
    }

    /**
     * 按点号路径下钻取对象，如 {@code nested(root, "data", "shipping")}。
     */
    public static Map<String, Object> nested(Map<String, Object> node, String... keys) {
        Object current = path(node, keys);
        if (current instanceof Map<?, ?> map) {
            return MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {
            });
        }
        return Map.of();
    }

    /**
     * 点号路径取值：{@code path(node, "shipping_address", "address1")}
     */
    @SuppressWarnings("unchecked")
    public static Object path(Map<String, Object> node, String... keys) {
        Object current = node;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }

    public static String text(Map<String, Object> node, String... keys) {
        Object value = path(node, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 数字/布尔取字符串形式（平台订单号常是 Long，导入侧按字符串处理）。
     */
    public static String idText(Map<String, Object> node, String... keys) {
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
    public static Integer integer(Map<String, Object> node, String... keys) {
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

    /**
     * 取金额字段的字符串形式，保留平台给的小数位（不做四舍五入，口径交给导入侧）。
     */
    public static String decimalText(Map<String, Object> node, String... keys) {
        return idText(node, keys);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(Object value) {
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

    /**
     * 序列化为 JSON 请求体。
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("平台请求体序列化失败: " + e.getMessage(), "PLATFORM_REQUEST_INVALID");
        }
    }

    /**
     * Unix 秒 → UTC 墙上时间字符串（TOP / Shopee / Ozon 的时间字段多为 epoch 秒）。
     */
    public static String epochSecondsToImportDateTime(Object seconds) {
        LocalDateTime value = epochSecondsToLocalDateTime(seconds);
        return value == null ? null : value.format(IMPORT_FORMATTER);
    }

    public static LocalDateTime epochSecondsToLocalDateTime(Object seconds) {
        Long epoch = nullableLong(seconds);
        if (epoch == null) {
            return null;
        }
        // 毫秒级时间戳（13 位）按毫秒处理：TOP 系部分接口用毫秒，避免解析成公元 4 万年
        long secondsValue = epoch > 99_999_999_999L ? epoch / 1000L : epoch;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(secondsValue), ZoneOffset.UTC);
    }

    public static Long nullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim()).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toImportDateTime(String isoTimestamp) {
        LocalDateTime value = toUtcLocalDateTime(isoTimestamp);
        return value == null ? null : value.format(IMPORT_FORMATTER);
    }

    /**
     * 同 {@link #toImportDateTime}，但保留 {@link LocalDateTime} 形态给需要类型化时间戳的调用方。
     */
    public static LocalDateTime toUtcLocalDateTime(String isoTimestamp) {
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
            throw new BusinessException("无法解析平台时间字段: " + text, "PLATFORM_TIMESTAMP_INVALID");
        }
    }

    /**
     * ISO8601 + UTC，Shopify 的 {@code updated_at_min} 这类查询参数用。
     */
    public static String toIsoUtcQueryTimestamp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Ozon 强制要求带时区偏移（裸 {@code LocalDateTime} 会被判 {@code bad time}）。
     * <p>
     * 偏移按 {@code ISO_OFFSET_DATE_TIME} 写出，UTC 会塌成 {@code Z}（形如
     * {@code 2026-01-25T12:00:00Z}）而不是 {@code +00:00}；两者表示同一个时刻，但平台若拒
     * {@code Z} 就得换调用方的 offset 参数——目前只有需要 {@code +00:00} 字面量的
     * Mercado Libre 在自己的 {@code MercadoApi} 里另写了格式化，没有共用本方法。
     */
    public static String toOffsetQueryTimestamp(LocalDateTime value, ZoneOffset offset) {
        if (value == null) {
            return null;
        }
        return value.atOffset(offset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static long toEpochSeconds(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.toEpochSecond(ZoneOffset.UTC);
    }

    public static Map<String, Object> linkedRow() {
        return new LinkedHashMap<>();
    }

    private static Object read(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(jsonBody, Object.class);
        } catch (Exception e) {
            throw new BusinessException("平台响应不是合法 JSON: " + truncate(jsonBody), "PLATFORM_RESPONSE_INVALID");
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
