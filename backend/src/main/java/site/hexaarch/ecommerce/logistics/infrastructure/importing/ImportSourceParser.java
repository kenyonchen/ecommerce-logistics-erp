package site.hexaarch.ecommerce.logistics.infrastructure.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品批量导入文件解析：支持 JSON（平台接口通用结构）与 CSV（电商后台导出常用格式）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ImportSourceParser {

    public static final String FORMAT_JSON = "JSON";
    public static final String FORMAT_CSV = "CSV";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据内容自动识别格式
     */
    public String detectFormat(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return FORMAT_CSV;
        }
        char first = trimmed.charAt(0);
        return (first == '[' || first == '{') ? FORMAT_JSON : FORMAT_CSV;
    }

    /**
     * 解析文件/文本内容为原始行
     */
    public List<Map<String, Object>> parse(String content) {
        return parse(content, detectFormat(content));
    }

    public List<Map<String, Object>> parse(String content, String format) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String text = stripBom(content).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return FORMAT_JSON.equalsIgnoreCase(format) ? parseJson(text) : parseCsv(text);
    }

    /**
     * 解析JSON：支持数组或 {data|products|items|results|list: []} 包装结构
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseJson(String text) {
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            Object collection = parsed;
            if (parsed instanceof Map<?, ?> map) {
                if (map.get("data") != null) {
                    collection = unwrap(map.get("data"));
                } else {
                    for (String key : List.of("products", "items", "results", "records", "list")) {
                        if (map.get(key) != null) {
                            collection = unwrap(map.get(key));
                            break;
                        }
                    }
                }
            }
            if (!(collection instanceof List<?> list)) {
                if (collection instanceof Map<?, ?>) {
                    return List.of(toObjectMap(collection));
                }
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(toObjectMap(map));
                }
            }
            return rows;
        } catch (Exception e) {
            log.warn("[ImportFile] JSON解析失败: {}", e.getMessage());
            throw new IllegalArgumentException("文件内容解析失败，请确认是合法的JSON或CSV格式: " + e.getMessage());
        }
    }

    /**
     * 解析CSV：首行为表头，支持双引号包裹与转义
     */
    public List<Map<String, Object>> parseCsv(String text) {
        List<List<String>> records = parseCsvRecords(text);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> headers = records.get(0);
        List<Map<String, Object>> rows = new ArrayList<>(records.size() - 1);
        for (int i = 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String value = column < values.size() ? values.get(column) : "";
                String header = headers.get(column);
                if (header != null && !header.isBlank()) {
                    row.put(header.trim(), value == null ? "" : value.trim());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private Object unwrap(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("products", "items", "results", "records", "list")) {
                if (map.get(key) instanceof List<?>) {
                    return map.get(key);
                }
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    /**
     * CSV行解析：支持引号内的分隔符与换行
     */
    private List<List<String>> parseCsvRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    private String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }
}
