package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shopify 商品拉取适配器：{@code GET /admin/api/{version}/products.json}。
 * <p>
 * 一个变体(variant)出一行：ERP 侧的 SKU 编码/售价/重量都是变体级属性，商品级字段随行重复，
 * 由导入侧按 {@code external_id} 归并。输出的键名与 {@code SamplePlatformProductFetcher} 的
 * SHOPIFY 分支保持一致（{@code external_id/title/sku/description/product_type/price/weight/attributes/active}），
 * 因此字段映射建议与示例数据链路可以复用同一份配置。
 * <p>
 * 两处必须做转换而不是照搬平台原值：
 * <ul>
 *   <li>{@code weight} + {@code weight_unit} 合成克（目标字段就是 WEIGHT_IN_GRAMS，不能把千克当克写进库）；</li>
 *   <li>{@code option1..3} 拼成 {@code 名称:值} 串（变体属性在产品侧只有 options 名字，值在变体侧）。</li>
 * </ul>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopifyProductFetcher implements PlatformProductFetcher {

    private static final String PRODUCTS_RESOURCE = "products.json";
    private static final int DEFAULT_TOTAL_LIMIT = 50;
    /**
     * Shopify 变体重量单位到克的换算表；平台只返回这四种单位。
     */
    private static final Map<String, BigDecimal> UNIT_TO_GRAMS = Map.of(
            "g", BigDecimal.ONE,
            "kg", new BigDecimal("1000"),
            "lb", new BigDecimal("453.59237"),
            "oz", new BigDecimal("28.349523125"));

    private final ShopifyAdminApi api;

    public ShopifyProductFetcher(ShopifyAdminApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPIFY.name();
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        throw new BusinessException("Shopify 商品适配器需要凭据上下文(accessToken)，请经适配器注册表调用",
                "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("limit", String.valueOf(ShopifyAdminApi.clampPageSize(totalLimit)));
        String keyword = request.keyword() == null ? null : request.keyword().trim().toLowerCase();

        List<Map<String, Object>> rows = new ArrayList<>();
        URI next = api.firstRequestUri(credential, PRODUCTS_RESOURCE, query);
        int pages = 0;
        while (next != null && pages < api.maxPages() && rows.size() < totalLimit) {
            ResponseEntity<String> response = api.get(next, credential);
            for (Map<String, Object> product : ShopifyJson.collection(response.getBody(), "products")) {
                for (Map<String, Object> row : toRows(product, keyword)) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
                if (rows.size() >= totalLimit) {
                    break;
                }
            }
            pages++;
            next = cursorUrl(response, credential);
        }
        if (next != null && rows.size() >= totalLimit) {
            log.info("[Shopify] 商品拉取已满足 limit={}，停止翻页 pages={}", totalLimit, pages);
        } else if (next != null) {
            log.warn("[Shopify] 商品拉取达到游标页数上限 {}，尚有后续页未取回 rows={}", api.maxPages(), rows.size());
        }
        log.info("[Shopify] 商品拉取完成 pages={} rows={} store={}", pages, rows.size(),
                credential == null ? null : credential.storeUrl());
        return rows;
    }

    /**
     * 产品对象展开为变体行；无变体的产品出一行（只带商品级字段）。
     */
    List<Map<String, Object>> toRows(Map<String, Object> product, String keyword) {
        List<Map<String, Object>> variants = ShopifyJson.objects(ShopifyJson.path(product, "variants"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (variants.isEmpty()) {
            Map<String, Object> row = baseRow(product);
            if (matches(row, keyword)) {
                rows.add(row);
            }
            return rows;
        }
        for (Map<String, Object> variant : variants) {
            Map<String, Object> row = baseRow(product);
            putIfPresent(row, "sku", ShopifyJson.text(variant, "sku"));
            putIfPresent(row, "variant_id", ShopifyJson.idText(variant, "id"));
            putIfPresent(row, "variant_title", ShopifyJson.text(variant, "title"));
            putIfPresent(row, "price", ShopifyJson.text(variant, "price"));
            putIfPresent(row, "currency", ShopifyJson.text(variant, "currency_code"));
            putIfPresent(row, "weight", toGrams(variant));
            putIfPresent(row, "attributes", toAttributePairs(product, variant));
            if (matches(row, keyword)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> baseRow(Map<String, Object> product) {
        Map<String, Object> row = ShopifyJson.linkedRow();
        row.put("external_id", ShopifyJson.idText(product, "id"));
        row.put("title", ShopifyJson.text(product, "title"));
        putIfPresent(row, "description", plainText(ShopifyJson.text(product, "body_html")));
        putIfPresent(row, "product_type", firstNonBlank(ShopifyJson.text(product, "product_type"),
                ShopifyJson.text(product, "type")));
        row.put("active", "active".equalsIgnoreCase(ShopifyJson.text(product, "status")));
        return row;
    }

    /**
     * {@code weight} 是字符串数值，单位在 {@code weight_unit}；缺单位时 Shopify 语义为克。
     */
    static String toGrams(Map<String, Object> variant) {
        String raw = ShopifyJson.text(variant, "weight");
        if (raw == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
        String unit = ShopifyJson.text(variant, "weight_unit");
        BigDecimal factor = UNIT_TO_GRAMS.get(unit == null ? "g" : unit.toLowerCase());
        if (factor == null) {
            log.warn("[Shopify] 未知重量单位 {}，按克处理 sku={}", unit, ShopifyJson.text(variant, "sku"));
            factor = BigDecimal.ONE;
        }
        return value.multiply(factor).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * 产品 {@code options[]} 给属性名，变体 {@code option1..3} 给属性值，按位次拼成 {@code 名:值,名:值}。
     */
    static String toAttributePairs(Map<String, Object> product, Map<String, Object> variant) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> option : ShopifyJson.objects(ShopifyJson.path(product, "options"))) {
            names.add(ShopifyJson.text(option, "name"));
        }
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            String value = ShopifyJson.text(variant, "option" + i);
            if (value == null) {
                continue;
            }
            String name = i - 1 < names.size() ? names.get(i - 1) : null;
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(name == null ? "option" + i : name).append(':').append(value);
        }
        return text.length() == 0 ? null : text.toString();
    }

    /**
     * 商品详情在 Shopify 里是 HTML，ERP 侧按纯文本存储，去标签并还原常见实体。
     */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = html.replaceAll("(?i)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean matches(Map<String, Object> row, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(row.get("title"), keyword) || contains(row.get("sku"), keyword);
    }

    private static boolean contains(Object value, String keyword) {
        return value != null && String.valueOf(value).toLowerCase().contains(keyword);
    }

    /**
     * 续页 URL 由 {@code Link} 头给出，原样使用（其中已含 {@code page_info} 与 {@code limit}）。
     */
    private URI cursorUrl(ResponseEntity<String> response, PlatformCredential credential) {
        String url = api.nextCursorUrl(response.getHeaders());
        if (url == null || url.isBlank()) {
            return null;
        }
        String absolute = url.startsWith("http") ? url
                : api.adminBase(credential) + (url.startsWith("/") ? url : "/" + url);
        return URI.create(absolute);
    }

    private static void putIfPresent(Map<String, Object> row, String key, String value) {
        if (value != null && !value.isBlank()) {
            row.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
