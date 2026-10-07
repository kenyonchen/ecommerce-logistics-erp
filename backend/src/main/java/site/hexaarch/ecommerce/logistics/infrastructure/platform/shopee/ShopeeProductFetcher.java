package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shopee 商品拉取适配器：{@code POST /api/v2/product/get_item_list}。
 * <p>
 * 路径在 <b>{@code product/} 而不是 {@code item/}</b>（§6 点名这个坑：接口叫 {@code get_item_list} 但挂在
 * {@code product/} 下，按直觉写成 {@code item/get_item_list} 会 404）。必填参数是
 * {@code offset}、{@code page_size}(≤100)、{@code item_status[]}（§6）。翻页看响应 {@code has_next_page} + {@code next_offset}。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§6 核实的是端点、必填参数与翻页键，不是商品对象/变体对象的结构。
 * 因此商品数组键（{@code item}/{@code items}）、商品 ID/标题/SKU/价格键、变体数组键一律按候选探测，
 * 探不到就留空而不是断言某种形状。行键对齐 {@code ImportTargetField} 别名表，与 {@code ShopifyProductFetcher.toRows}
 * 同一套中立形状：一个变体出一行（{@code variations[]} 存在时），没有变体的商品出一行。
 * <p>
 * {@code item_status[]} 是必填但 §6 没给取值集合，这里发最小可辩护的单一取值 {@code ["NORMAL"]}
 * （只要在售商品），待填钥匙实测校正完整枚举（ISS-065）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopeeProductFetcher implements PlatformProductFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 50;

    /**
     * §6：{@code page_size} 上限 100；未指定时取一个不超过它的保守值
     */
    private static final int PAGE_SIZE = 100;

    private static final String[] ITEM_ARRAY_KEYS = {"item", "items", "data"};
    private static final String[] VARIATION_ARRAY_KEYS = {"variations", "model", "model_list"};

    private final ShopeeOpenApi api;

    public ShopeeProductFetcher(ShopeeOpenApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPEE.name();
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        throw new BusinessException("Shopee 商品适配器需要凭据上下文(apiKey=partner_id、apiSecret=partner_key、"
                + "externalShopId=shop_id、accessToken)，请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        String keyword = trimToNull(request.keyword());
        int pageSize = Math.min(totalLimit > 0 ? totalLimit : PAGE_SIZE, PAGE_SIZE);

        List<Map<String, Object>> rows = new ArrayList<>();
        int offset = request.page() != null && request.page() > 1
                ? (request.page() - 1) * pageSize : 0;
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            Map<String, Object> body = listBody(offset, pageSize);
            Map<String, Object> envelope = ShopeeOpenApi.readEnvelope(
                    api.postJson(credential, ShopeeOpenApi.PRODUCT_LIST_PATH, body).getBody(),
                    ShopeeOpenApi.PRODUCT_LIST_PATH);
            Map<String, Object> response = ShopeeOpenApi.response(envelope);
            List<Map<String, Object>> items = itemArray(response);
            if (items.isEmpty()) {
                break;
            }
            for (Map<String, Object> item : items) {
                for (Map<String, Object> row : toRows(item)) {
                    if (!matches(row, keyword)) {
                        continue;
                    }
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
            int appliedOffset = offset;
            hasMore = truthy(response.get("has_next_page")) || truthy(response.get("more"));
            Integer nextOffset = probeInt(response, "next_offset", "offset");
            if (hasMore) {
                // 没有 next_offset 就按本页条数顺延，仍不推进则收手，避免在页数上限上空转
                offset = nextOffset != null ? nextOffset : appliedOffset + items.size();
                hasMore = offset != appliedOffset;
            }
        }
        if (hasMore) {
            log.warn("[Shopee] 商品拉取达到翻页上限 {}，尚有后续页未取回 rows={}", api.maxPages(), rows.size());
        }
        log.info("[Shopee] 商品拉取完成 pages={} rows={} shop_id={}", pages, rows.size(),
                credential == null ? null : credential.externalShopId());
        return rows;
    }

    /**
     * 商品对象展开为变体行；无变体的商品出一行（只带商品级字段）。探测式取值，探不到的键不透出。
     */
    List<Map<String, Object>> toRows(Map<String, Object> item) {
        List<Map<String, Object>> variations = variations(item);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (variations.isEmpty()) {
            rows.add(baseRow(item));
            return rows;
        }
        for (Map<String, Object> variation : variations) {
            Map<String, Object> row = baseRow(item);
            putIfPresent(row, "sku", firstNonBlank(PlatformJson.text(variation, "sku"), PlatformJson.text(item, "sku")));
            putIfPresent(row, "variant_id", probeId(variation, "model_id", "id"));
            putIfPresent(row, "variant_title", probeText(variation, "name", "model_name"));
            putIfPresent(row, "price", firstNonBlank(PlatformJson.decimalText(variation, "price"),
                    PlatformJson.decimalText(item, "price")));
            putIfPresent(row, "currency", PlatformJson.text(variation, "currency"));
            putIfPresent(row, "weight", firstNonBlank(PlatformJson.decimalText(variation, "weight"),
                    PlatformJson.decimalText(item, "weight")));
            putIfPresent(row, "attributes", probeText(variation, "attribute_list", "variation_attributes"));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> baseRow(Map<String, Object> item) {
        Map<String, Object> row = PlatformJson.linkedRow();
        putIfPresent(row, "external_id", firstNonBlank(probeId(item, "item_id", "id"),
                PlatformJson.idText(item, "product_id")));
        putIfPresent(row, "title", firstNonBlank(probeText(item, "item_name", "name"),
                PlatformJson.text(item, "title")));
        putIfPresent(row, "description", firstNonBlank(PlatformJson.text(item, "description"),
                PlatformJson.text(item, "desc")));
        putIfPresent(row, "product_type", probeText(item, "category_name", "product_type"));
        putIfPresent(row, "sku", PlatformJson.text(item, "sku"));
        putIfPresent(row, "price", probeDecimal(item, "price", "original_price"));
        putIfPresent(row, "currency", PlatformJson.text(item, "currency"));
        putIfPresent(row, "weight", PlatformJson.decimalText(item, "weight"));
        putIfPresent(row, "active", probeText(item, "item_status", "status"));
        return row;
    }

    private static List<Map<String, Object>> variations(Map<String, Object> item) {
        for (String key : VARIATION_ARRAY_KEYS) {
            Object value = item.get(key);
            if (value instanceof List<?>) {
                return PlatformJson.objects(value);
            }
        }
        return List.of();
    }

    /**
     * 列表请求体：{@code offset}/{@code page_size}/{@code item_status[]}（§6 必填）。
     * {@code item_status[]} 取值集合未被 §6 核实，这里只发在售项 {@code ["NORMAL"]}（ISS-065 待实测补全枚举）。
     */
    private static Map<String, Object> listBody(int offset, int pageSize) {
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("offset", offset);
        body.put("page_size", pageSize);
        body.put("item_status", List.of("NORMAL"));
        return body;
    }

    /**
     * 解出 {@code response} 里的商品数组（连通性/结构探测可复用）：按候选键探测，探不到返回空列表。
     */
    static List<Map<String, Object>> itemsIn(String jsonBody) {
        Map<String, Object> envelope = ShopeeOpenApi.readEnvelope(jsonBody, ShopeeOpenApi.PRODUCT_LIST_PATH);
        return itemArray(ShopeeOpenApi.response(envelope));
    }

    private static List<Map<String, Object>> itemArray(Map<String, Object> response) {
        for (String key : ITEM_ARRAY_KEYS) {
            Object value = response.get(key);
            if (value instanceof List<?>) {
                return PlatformJson.objects(value);
            }
        }
        return List.of();
    }

    /**
     * 候选键探测：逐个键单取（{@link PlatformJson} 各取值的变参是<b>逐层下钻</b>而非候选表，
     * 所以这里显式循环）。响应体键名未被 §6 核实（ISS-065），按候选探测而不是断定一种。
     */
    private static String probeText(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String probeId(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.idText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String probeDecimal(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.decimalText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer probeInt(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Integer value = PlatformJson.integer(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)));
    }

    private static boolean matches(Map<String, Object> row, String keyword) {
        if (keyword == null) {
            return true;
        }
        String needle = keyword.toLowerCase();
        return contains(row.get("title"), needle) || contains(row.get("sku"), needle);
    }

    private static boolean contains(Object value, String needle) {
        return value != null && String.valueOf(value).toLowerCase().contains(needle);
    }

    private static void putIfPresent(Map<String, Object> row, String key, String value) {
        if (value != null && !value.isBlank()) {
            row.put(key, value);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
