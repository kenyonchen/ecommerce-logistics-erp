package site.hexaarch.ecommerce.logistics.infrastructure.platform.ozon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ozon 商品拉取适配器：{@code POST /v3/product/list} 取商品 id，再
 * {@code POST /v4/product/info/attributes} 取属性。
 * <p>
 * 两步是 §8 的第三个坑逼出来的（docs/spec/platform-api-reference.md §8「<b>没有商品列表接口</b>」）：
 * {@code /v3/product/list} 只给 {@code product_id[]}，名称/SKU/重量一律拿不到，必须再喂给属性接口。
 * 属性接口的响应行 <b>给的是 {@code id} 而不是 {@code product_id}</b>（§8 点名的坑），
 * 所以 {@code toRow} 里取平台商品 ID 先看 {@code id}，翻页游标也先看 {@code id}。
 * <p>
 * 夹子：属性接口 {@code limit} ≤1000、默认 100（§8）；{@code /v3/product/list} 的上限 §8 没给，
 * 沿用同一保守上限 1000，且单页条数不会超过调用方要的总条数，避免为了几行商品去换回上千个 id。
 * 翻页用请求侧的 {@code last_id} 链式推进（§8 明文），响应侧的游标键名未核实，
 * 因此按候选键探测，探不到就退回本页最后一个 id——退不回就停止翻页而不是重复上一页。
 * <p>
 * <b>属性行的字段名没有官方出处可依</b>：§8 核实的是端点、{@code filter} 形状与「返回 {@code id}」这一条，
 * 不是属性对象的键集合。因此除商品 ID 外全部按候选键探测，探不到就不透出该键（{@code active} 探不到分类依据时
 * 也不透出，而不是硬塞布尔）。行键对齐 {@code ImportTargetField} 别名表，与 {@code ShopifyProductFetcher} 同一套中立形状。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class OzonProductFetcher implements PlatformProductFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 50;

    /**
     * §8 核实了 {@code filter} 里有 {@code visibility} 这个键，但没核实它的取值集合；
     * {@code ALL} 是「不按可见性收窄」的唯一不丢数据的写法，待实测校正（ISS-065）。
     */
    private static final String VISIBILITY = "ALL";

    /**
     * 条目数组在信封里的候选键：两步的 §8 描述都没给出集合键名，只探测不假定
     */
    private static final String[] ITEM_KEYS = {"items", "products", "result"};

    /**
     * 响应侧翻页游标的候选键：§8 只核实了请求用 {@code last_id}
     */
    private static final String[] CURSOR_KEYS = {"last_id", "cursor"};

    /**
     * Ozon 重量单位到克的换算，与 {@code ShopifyProductFetcher} 同一口径：目标字段是 WEIGHT_IN_GRAMS，
     * 不能把千克当克写进库。
     */
    private static final Map<String, BigDecimal> UNIT_TO_GRAMS = Map.of(
            "g", BigDecimal.ONE,
            "kg", new BigDecimal("1000"),
            "lb", new BigDecimal("453.59237"),
            "oz", new BigDecimal("28.349523125"));

    private final OzonSellerApi api;

    public OzonProductFetcher(OzonSellerApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.OZON.name();
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        throw new BusinessException("Ozon 商品适配器需要凭据上下文(apiKey=Client-Id、apiSecret=Api-Key)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        String keyword = trimToNull(request.keyword());
        // 第一步的上限 §8 没给，沿用订单列表同一保守上限，且不超过调用方要的总条数
        int pageLimit = Math.min(totalLimit, OzonSellerApi.MAX_FILTER_LIMIT);
        // 第二步的 limit 按 §8 自己给的口径夹取：上限 1000、缺省 100
        int attributesLimit = OzonSellerApi.clampAttributesLimit(request.limit());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        String lastId = "";
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            // 第一步：只要 id 清单（§8：/v3/product/list 只给 product_id[]）
            String listBody = api.postJson(credential, OzonSellerApi.PRODUCT_LIST_PATH,
                    listBody(lastId, pageLimit)).getBody();
            List<Map<String, Object>> idEntries = itemsIn(listBody);
            if (idEntries.isEmpty()) {
                break;
            }
            List<String> productIds = productIds(idEntries);
            if (!productIds.isEmpty()) {
                for (Map<String, Object> attribute : attributeRows(credential, productIds, attributesLimit)) {
                    Map<String, Object> row = toRow(attribute);
                    if (!markSeen(seenIds, row.get("external_id")) || !matches(row, keyword)) {
                        continue;
                    }
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            pages++;
            String returned = cursor(rootOf(listBody), idEntries, "product_id", "id");
            hasMore = returned != null && !returned.equals(lastId);
            if (hasMore) {
                lastId = returned;
            }
        }
        if (hasMore) {
            log.warn("[Ozon] 商品拉取达到翻页上限 {}，尚有后续页未取回 rows={}", api.maxPages(), rows.size());
        }
        log.info("[Ozon] 商品拉取完成 pages={} rows={}", pages, rows.size());
        return rows;
    }

    /**
     * 属性对象 → 中立行。商品 ID 只从 {@code id} 取（§8 的坑：属性行没有 {@code product_id}），
     * {@code product_id} 只是万一平台改了键名时的兜底。
     */
    Map<String, Object> toRow(Map<String, Object> attribute) {
        Map<String, Object> row = PlatformJson.linkedRow();
        row.put("external_id", firstNonBlank(PlatformJson.idText(attribute, "id"),
                PlatformJson.idText(attribute, "product_id")));
        putIfPresent(row, "title", firstNonBlank(PlatformJson.text(attribute, "name"),
                PlatformJson.text(attribute, "title")));
        putIfPresent(row, "sku", firstNonBlank(PlatformJson.text(attribute, "offer_id"),
                PlatformJson.text(attribute, "sku"),
                PlatformJson.text(attribute, "seller_sku")));
        putIfPresent(row, "description", PlatformJson.text(attribute, "description"));
        putIfPresent(row, "product_type", firstNonBlank(PlatformJson.text(attribute, "product_type"),
                PlatformJson.text(attribute, "type_name")));
        putIfPresent(row, "price", firstNonBlank(PlatformJson.text(attribute, "price"),
                PlatformJson.text(attribute, "sale_price")));
        putIfPresent(row, "currency", PlatformJson.text(attribute, "currency_code"));
        putIfPresent(row, "weight", toGrams(attribute));
        putIfPresent(row, "attributes", toAttributeText(attribute));
        Boolean active = bridgeActive(firstNonBlank(PlatformJson.text(attribute, "status"),
                PlatformJson.text(attribute, "visibility")));
        if (active != null) {
            row.put("active", active);
        }
        return row;
    }

    /**
     * 重量单位换算成克；缺单位时按克处理（与 {@code ShopifyProductFetcher.toGrams} 同一口径），
     * 未知单位打 WARN 而不是静默换算。
     */
    static String toGrams(Map<String, Object> attribute) {
        String raw = PlatformJson.text(attribute, "weight");
        if (raw == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
        String unit = PlatformJson.text(attribute, "weight_unit");
        BigDecimal factor = UNIT_TO_GRAMS.get(unit == null ? "g" : unit.toLowerCase());
        if (factor == null) {
            log.warn("[Ozon] 未知重量单位 {}，按克处理 sku={}", unit, PlatformJson.text(attribute, "offer_id"));
            factor = BigDecimal.ONE;
        }
        return value.multiply(factor).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * 属性数组拼成导入侧要的 {@code 名:值,名:值} 串。Ozon 的 {@code attributes[]} 内部结构未被 §8 核实，
     * 因此能拼出多少算多少，一个都拼不出来就不透出这个键。
     */
    static String toAttributeText(Map<String, Object> attribute) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> item : PlatformJson.objects(PlatformJson.path(attribute, "attributes"))) {
            String name = PlatformJson.text(item, "name");
            String value = attributeValues(item);
            if (name == null || value == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(name).append(':').append(value);
        }
        return text.length() == 0 ? null : text.toString();
    }

    private static String attributeValues(Map<String, Object> item) {
        StringBuilder joined = new StringBuilder();
        for (Map<String, Object> value : PlatformJson.objects(item.get("values"))) {
            String text = firstNonBlank(PlatformJson.text(value, "text"),
                    PlatformJson.idText(value, "dictionary_value_id"));
            if (text == null) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append('/');
            }
            joined.append(text);
        }
        return joined.length() == 0 ? PlatformJson.text(item, "value") : joined.toString();
    }

    /**
     * 可见性/状态 → 布尔。判定顺序不可调换：{@code inactive} 字面含 {@code active}，必须先按停用归类。
     * 认不出的取值返回 {@code null}（不透出该键），因为把未知状态硬塞成「启用」会让停售商品被当在售。
     */
    static Boolean bridgeActive(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase().replace('_', ' ').replace('-', ' ');
        if (normalized.contains("hidden") || normalized.contains("inactive") || normalized.contains("disabled")
                || normalized.contains("archived") || normalized.contains("blocked") || normalized.contains("removed")) {
            return false;
        }
        if (normalized.contains("visible") || normalized.contains("active")) {
            return true;
        }
        return null;
    }

    /**
     * 第二步请求体：§8 的形状是 {@code {filter:{product_id[], visibility}, limit, last_id}}。
     * 这里只带 {@code product_id}——本页的 id 已经由第一步按 {@code visibility} 筛过，
     * 重复一个取值未核实的机会只会引入新的口径分歧。
     */
    private static Map<String, Object> attributesBody(List<String> productIds, int limit, String lastId) {
        Map<String, Object> filter = PlatformJson.linkedRow();
        filter.put("product_id", productIds);
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("filter", filter);
        body.put("limit", limit);
        body.put("last_id", lastId);
        return body;
    }

    private static Map<String, Object> listBody(String lastId, int limit) {
        Map<String, Object> filter = PlatformJson.linkedRow();
        filter.put("visibility", VISIBILITY);
        Map<String, Object> body = PlatformJson.linkedRow();
        body.put("filter", filter);
        body.put("last_id", lastId);
        body.put("limit", limit);
        return body;
    }

    /**
     * 属性接口按本页 id 批量取属性；一个商品一行，所以取满本页 id 数就收手，
     * 游标不推进时也收手——重复上一页等于在页数上限上空转。
     */
    private List<Map<String, Object>> attributeRows(PlatformCredential credential, List<String> productIds, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        String lastId = "";
        int pages = 0;
        while (items.size() < productIds.size() && pages < api.maxPages()) {
            String body = api.postJson(credential, OzonSellerApi.PRODUCT_ATTRIBUTES_PATH,
                    attributesBody(productIds, limit, lastId)).getBody();
            List<Map<String, Object>> page = itemsIn(body);
            if (page.isEmpty()) {
                break;
            }
            items.addAll(page);
            pages++;
            String returned = cursor(rootOf(body), page, "id", "product_id");
            if (returned == null || returned.equals(lastId)) {
                break;
            }
            lastId = returned;
        }
        return items;
    }

    private static List<String> productIds(List<Map<String, Object>> entries) {
        List<String> ids = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String id = firstNonBlank(PlatformJson.idText(entry, "product_id"), PlatformJson.idText(entry, "id"));
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    static List<Map<String, Object>> itemsIn(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return List.of();
        }
        for (String key : ITEM_KEYS) {
            List<Map<String, Object>> items = PlatformJson.collection(jsonBody, key);
            if (!items.isEmpty()) {
                return items;
            }
        }
        return List.of();
    }

    /**
     * 下一页游标：先探响应里 §8 之外的候选键，都没有就退回本页最后一个条目的 id 键
     * （§8：属性行只有 {@code id}，第一步的行只有 {@code product_id}，两种都可能出现）。
     */
    private static String cursor(Map<String, Object> root, List<Map<String, Object>> entries, String... idKeys) {
        for (String key : CURSOR_KEYS) {
            Object value = PlatformJson.path(root, "result", key);
            if (value == null) {
                value = root.get(key);
            }
            String text = scalarText(value);
            if (text != null) {
                return text;
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        Map<String, Object> last = entries.get(entries.size() - 1);
        for (String key : idKeys) {
            String text = PlatformJson.idText(last, key);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    private static String scalarText(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    /**
     * 记录已出行过的平台商品 ID；没有 ID 的行不能被当成「已经见过」，交给导入侧的必填校验去拦。
     */
    private static boolean markSeen(Set<String> seenIds, Object externalId) {
        return externalId == null || seenIds.add(String.valueOf(externalId));
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
