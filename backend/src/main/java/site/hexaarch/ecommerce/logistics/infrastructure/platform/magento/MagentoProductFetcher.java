package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Magento 商品拉取适配器：{@code GET /rest/<store>/V1/products}（docs/spec/platform-api-reference.md §9），
 * {@code searchCriteria} 形状与订单列表同构。
 * <p>
 * <b>本类存在的唯一理由是 §9 核实到的那条坑</b>：{@code condition_type=like} <b>要求调用方自带的值里有
 * 通配符 {@code *} 或 {@code %}</b>，裸词「t-shirt」在 like 下匹配不到任何东西——不自己补通配符的话
 * 关键词搜索会稳定返回空集，而且平台不报错，看起来像「店铺没有这个商品」。
 * 因此 {@link #likeValue} 负责补 {@code %keyword%}；关键词本身已经带通配符时原样发出去，
 * 不再叠一层（否则 {@code %foo} 会变成 {@code %%foo%%}，把操作员显式写的匹配意图改掉）。
 * 注：§9 也记了「精确匹配」运算符公开文档未给出（ISS-065），所以这里只有 like 一条路。
 * <p>
 * <b>configurable/child 商品不展开变体</b>：一个商品出一行，取商品级的 {@code sku} 与 {@code name}。
 * §9 核实的是端点与 {@code searchCriteria} 形态，没有核实 {@code extensions.product_links} 的子件形状，
 * 所以不做臆造的变体展开（{@code ShopifyProductFetcher} 能展开是因为它的 {@code variants[]} 是核实过的）。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：除商品 ID/SKU/名称外一律按候选键探测，探不到就不透出该键。
 * 两个已知缺口按「不编造」处理并记入 ISS-065：
 * {@code description} 在 Magento 里挂在 {@code custom_attributes[]} 上（该结构 §9 未核实，故不解析，
 * 该键多半为空）；{@code weight} 没有单位字段（计量单位是店铺级配置、不随响应返回），
 * 所以原样透传数值而<b>不</b>换算成克——把千克当克写进库比留空更糟。
 * 行键对齐 {@code ImportTargetField} 别名表，与 {@code ShopifyProductFetcher} 同一套中立形状。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MagentoProductFetcher implements PlatformProductFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 50;

    private static final int FIRST_PAGE = 1;

    /**
     * §9：关键词过滤走商品名字段
     */
    static final String FIELD_NAME = "name";

    /**
     * §9 的 {@code condition_type} 全集里的模糊匹配；它要求值自带通配符
     */
    private static final String CONDITION_LIKE = "like";

    private static final String WILDCARD = "%";

    private final MagentoRestApi api;

    public MagentoProductFetcher(MagentoRestApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.MAGENTO.name();
    }

    /**
     * Magento 必须要令牌与自建 base URL，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        throw new BusinessException("Magento 商品适配器需要凭据上下文(storeUrl 与 accessToken 或 apiKey/apiSecret)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        MagentoAdminToken token = api.tokenFor(credential);
        int pageSize = MagentoRestApi.pageSize(request.limit());
        Map<String, String> keywordFilters = new LinkedHashMap<>();
        putKeywordFilter(keywordFilters, request.keyword());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        int firstPage = request.page() == null || request.page() < FIRST_PAGE ? FIRST_PAGE : request.page();
        int page = firstPage;
        long consumed = 0;
        boolean hasMore = true;
        while (hasMore && page - firstPage < api.maxPages() && rows.size() < totalLimit) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put(MagentoRestApi.PAGE_SIZE_KEY, String.valueOf(pageSize));
            query.put(MagentoRestApi.CURRENT_PAGE_KEY, String.valueOf(page));
            query.putAll(keywordFilters);
            String body = api.get(api.productsUri(credential, query), token).getBody();
            List<Map<String, Object>> items = MagentoOrderFetcher.itemsIn(body);
            if (items.isEmpty()) {
                hasMore = false;
                break;
            }
            consumed += items.size();
            Integer totalCount = PlatformJson.integer(rootOf(body), MagentoOrderFetcher.TOTAL_COUNT_KEY);
            int unseen = 0;
            for (Map<String, Object> product : items) {
                Map<String, Object> row = toRow(product);
                if (!markSeen(seenIds, row.get("external_id"))) {
                    continue;
                }
                unseen++;
                rows.add(row);
                if (rows.size() >= totalLimit) {
                    break;
                }
            }
            if (unseen == 0) {
                // 整页都是见过的商品，说明 currentPage 没让平台前进，继续翻只是在页数上限上空转
                hasMore = false;
                break;
            }
            page++;
            hasMore = totalCount == null || consumed < totalCount;
        }
        if (hasMore && rows.size() < totalLimit) {
            log.warn("[Magento] 商品拉取达到翻页上限 {}，尚有后续页未取回 rows={}", api.maxPages(), rows.size());
        }
        log.info("[Magento] 商品拉取完成 pages={} rows={} storeView={}",
                page - firstPage, rows.size(), api.storeView(credential));
        return rows;
    }

    /**
     * 商品对象 → 中立行。键名全部是候选探测（§9 未核实商品对象结构），探不到就不透出该键而不是塞个假值。
     */
    Map<String, Object> toRow(Map<String, Object> product) {
        Map<String, Object> row = PlatformJson.linkedRow();
        row.put("external_id", firstNonBlank(PlatformJson.idText(product, "id"),
                PlatformJson.idText(product, "entity_id")));
        putIfPresent(row, "title", firstNonBlank(PlatformJson.text(product, "name"),
                PlatformJson.text(product, "title")));
        putIfPresent(row, "sku", firstNonBlank(PlatformJson.text(product, "sku"),
                PlatformJson.text(product, "sku_code")));
        putIfPresent(row, "description", PlatformJson.text(product, "description"));
        // type_id 是 simple/configurable/grouped/bundle 这一族商品类型，落到导入侧的分类名槽位
        putIfPresent(row, "product_type", PlatformJson.text(product, "type_id"));
        // 金额走 text 而不是 idText：price 是 JSON 数字，idText 会按 longValue 截掉小数
        putIfPresent(row, "price", PlatformJson.text(product, "price"));
        putIfPresent(row, "weight", PlatformJson.text(product, "weight"));
        Boolean active = bridgeActive(PlatformJson.integer(product, "status"));
        if (active != null) {
            row.put("active", active);
        }
        return row;
    }

    /**
     * 有关键词时发一个 like 过滤（§9 的数组语法，field/value/condition_type 各出现一次）。
     * 关键词为空时整个条件不发——§9 说没有精确匹配运算符可用（ISS-065），发一个空的 like 只会筛掉全部商品。
     */
    private static void putKeywordFilter(Map<String, String> query, String keyword) {
        String value = trimToNull(keyword);
        if (value == null) {
            return;
        }
        MagentoRestApi.putFilter(query, 0, 0, FIELD_NAME, likeValue(value), CONDITION_LIKE);
    }

    /**
     * §9 的坑：{@code like} 的值必须自带 {@code *} 或 {@code %}，否则匹配不到任何东西。
     * 已经自带通配符的关键词原样发出（保留操作员显式写的匹配意图，也不叠加成 {@code %%…%%}）。
     */
    static String likeValue(String keyword) {
        String value = trimToNull(keyword);
        if (value == null) {
            return null;
        }
        if (value.indexOf('*') >= 0 || value.indexOf('%') >= 0) {
            return value;
        }
        return WILDCARD + value + WILDCARD;
    }

    /**
     * Magento 的 {@code status} 是 {@code 1=enabled / 0=disabled} 的数字开关（§9 的字段白名单里点过 {@code status}，
     * 但取值集合未核实）。{@code null} 表示没探到这个键，不透出而不是硬判成启用——把停售商品当在售卖比留空更糟。
     */
    static Boolean bridgeActive(Integer status) {
        if (status == null) {
            return null;
        }
        return status != 0;
    }

    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
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
