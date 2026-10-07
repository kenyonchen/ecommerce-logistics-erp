package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Amazon SP-API 订单拉取适配器：{@code GET /orders/v0/orders}（PascalCase 参数形态）。
 * <p>
 * 选 v0 而不是 {@code /orders/2026-01-01/orders} 是因为 v0「仍广泛在用」且规范核实得最完整
 * （docs/spec/platform-api-reference.md §3.3）：{@code CreatedAfter}/{@code LastUpdatedAfter} <b>二选一必填</b>、
 * {@code MarketplaceIds} 必填（≤50）、{@code MaxResultsPerPage} 1-100、翻页用响应里的 {@code NextToken}。
 * 速率只有 0.0167 rps，所以单页取满 100 而不是按请求的小值多跑几页。
 * <p>
 * 输出行键对齐 {@code OrderImportTargetField} 的别名表，与 Shopify 适配器同一套中立形状，
 * 导入侧的字段映射与校验（订单号/收件人/详细地址/明细必填）可直接复用。
 * <p>
 * <b>响应体的字段名没有官方出处可依</b>：§3.3 只核实了端点与查询参数，没有核实订单对象结构。
 * 因此本类对「订单数组在信封里的位置」「明细数组的键名」「订单号/邮箱/支付字段」一律按候选键探测，
 * 探测不到就留空而不是断言某种形状；这些待实测项归入 ISS-065。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AmazonOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * 没有任何时间界时的回溯窗口（天），见 {@link #putTimeWindow}
     */
    private static final int DEFAULT_BACKFILL_DAYS = 30;

    /**
     * 订单数组在响应信封里的候选位置；{@code payload} 直接是数组也是已知的形态之一
     */
    private static final String[][] ORDER_ARRAY_PATHS = {
            {"payload", "Orders"},
            {"payload"},
            {"orders"}
    };

    private static final String[][] NEXT_TOKEN_PATHS = {
            {"payload", "NextToken"},
            {"NextToken"},
            {"payload", "nextToken"},
            {"nextToken"}
    };

    /**
     * 明细数组的候选键：v0 列表接口的每单明细字段名未被核实，只探测不假定
     */
    private static final String[][] LINE_ITEM_PATHS = {
            {"OrderItems", "Item"},
            {"OrderItems"},
            {"Items"}
    };

    private static final String[] ORDER_ID_KEYS = {"AmazonOrderId", "OrderId", "orderId"};
    private static final String[] EMAIL_KEYS = {"BuyerEmail", "Email", "email"};
    private static final String[] PAYMENT_METHOD_KEYS = {"PaymentMethod", "paymentMethod"};
    private static final String[] PAYMENT_STATUS_KEYS = {"PaymentExecutionStatus", "PaymentStatus", "paymentStatus"};

    private final AmazonSpApi api;

    public AmazonOrderFetcher(AmazonSpApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.AMAZON_SP_API.name();
    }

    /**
     * SP-API 必须要访问令牌与区域凭据，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("Amazon SP-API 订单适配器需要凭据上下文(accessToken/region/marketplaceIds)，"
                + "请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        Map<String, String> query = new LinkedHashMap<>();
        putTimeWindow(query, request);
        query.put("MarketplaceIds", AmazonSpApi.marketplaceIdsParam(credential));
        query.put("MaxResultsPerPage", String.valueOf(AmazonSpApi.clampMaxResultsPerPage(request.limit())));
        // v0 列表接口没有按订单号过滤的参数（§3.3 的可选参数只有 OrderStatuses），只能取回后筛，与 Shopify 同一口径
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        String nextToken = null;
        int pages = 0;
        boolean hasMore = true;
        while (hasMore && pages < api.maxPages() && rows.size() < totalLimit) {
            Map<String, String> pageQuery = new LinkedHashMap<>(query);
            if (nextToken != null) {
                pageQuery.put("NextToken", nextToken);
            }
            String appliedToken = nextToken;
            ResponseEntity<String> response = api.get(api.ordersUri(credential, pageQuery), credential);
            Map<String, Object> root = rootOf(response.getBody());
            for (Map<String, Object> order : PlatformJson.objects(firstListNode(root, ORDER_ARRAY_PATHS))) {
                Map<String, Object> row = toRow(order);
                // 平台跨页重复返回同一单（游标不推进时整页都会重复）不能重复出行，否则导入侧按幂等键会互相覆盖
                if (!markSeen(seenOrderIds, row.get("orderId"))) {
                    continue;
                }
                if (wantedOrderId == null || wantedOrderId.equals(row.get("orderId"))) {
                    rows.add(row);
                    if (rows.size() >= totalLimit) {
                        break;
                    }
                }
            }
            pages++;
            String returnedToken = firstTextAtPath(root, NEXT_TOKEN_PATHS);
            // 平台把同一个游标再吐一次时不能再跟着翻，否则会在页数上限上空转
            hasMore = returnedToken != null && !returnedToken.equals(appliedToken);
            nextToken = hasMore ? returnedToken : null;
        }
        if (hasMore) {
            log.warn("[Amazon] 订单拉取达到游标页数上限 {}，尚有后续页未取回 platformOrders={}",
                    api.maxPages(), rows.size());
        }
        log.info("[Amazon] 订单拉取完成 pages={} rows={} region={}",
                pages, rows.size(), api.regionLabel(credential));
        return rows;
    }

    /**
     * 把 SP-API 订单对象映射为导入侧的中立行。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", firstIdText(order, ORDER_ID_KEYS));
        row.put("customerName", PlatformJson.text(order, "BuyerName"));
        row.put("email", firstText(order, EMAIL_KEYS));
        row.put("orderStatus", bridgeStatus(PlatformJson.text(order, "Status")));
        row.put("orderTime", PlatformJson.toImportDateTime(PlatformJson.text(order, "PurchaseDate")));
        row.put("currency", PlatformJson.text(order, "Currency"));
        row.put("totalAmount", PlatformJson.text(order, "OrderTotal", "Amount"));
        row.put("paymentMethod", firstText(order, PAYMENT_METHOD_KEYS));
        row.put("paymentStatus", firstText(order, PAYMENT_STATUS_KEYS));
        putAddress(row, order);
        row.put("lineItems", PlatformJson.objects(firstListNode(order, LINE_ITEM_PATHS)));
        return row;
    }

    /**
     * 状态桥接：v0 的 {@code Status} 是平台自己的枚举文案，ERP 侧只认 {@code OrderStatus} 的六个码。
     * 判定顺序不可调换——{@code Unshipped} 字面上包含 {@code shipped}，必须先按未发货归类。
     * 认不出的一律原样透出而不是硬塞进 {@code pending}：导入侧 {@code toOrderStatus} 会兜底成待处理，
     * 但保留原文才能让人看见平台侧出现了新的状态值。
     */
    static String bridgeStatus(String amazonStatus) {
        if (amazonStatus == null || amazonStatus.isBlank()) {
            return null;
        }
        String value = amazonStatus.trim();
        String normalized = value.toLowerCase();
        if (normalized.contains("cancel")) {
            return "cancelled";
        }
        if (normalized.contains("unshipped")) {
            return "processing";
        }
        if (normalized.contains("shipped")) {
            return "shipped";
        }
        if (normalized.contains("pending")) {
            return "pending";
        }
        return value;
    }

    /**
     * 收件地址：v0 的 {@code ShippingAddress} 是嵌套对象，展平成标量键。
     */
    private void putAddress(Map<String, Object> row, Map<String, Object> order) {
        Map<String, Object> address = PlatformJson.nested(order, "ShippingAddress");
        row.put("recipientName", firstNonBlank(PlatformJson.text(address, "Name"),
                PlatformJson.text(order, "BuyerName")));
        row.put("phoneNumber", PlatformJson.text(address, "Phone"));
        row.put("country", firstNonBlank(PlatformJson.text(address, "CountryCode"),
                PlatformJson.text(address, "CountryName")));
        row.put("province", PlatformJson.text(address, "StateOrRegion"));
        row.put("city", PlatformJson.text(address, "City"));
        row.put("addressLine1", firstNonBlank(PlatformJson.text(address, "AddressLine1"),
                PlatformJson.text(address, "AddressLine2"),
                PlatformJson.text(address, "DistrictOrNeighborhood")));
        row.put("zipCode", PlatformJson.text(address, "PostalCode"));
    }

    /**
     * {@code CreatedAfter} 与 {@code LastUpdatedAfter} 二选一必填（§3.3）：有增量界就用
     * {@code LastUpdatedAfter}（增量同步按平台侧更新时间取），否则用 {@code CreatedAfter}。
     * <p>
     * 两个界都没有时不能省略参数——平台会直接拒掉请求——所以给一个 30 天回溯窗口：
     * 它覆盖常规同步节奏（按天/按周）的余量，又不至于去要平台订单保留期的全量数据而放大分页成本。
     */
    private void putTimeWindow(Map<String, String> query, PlatformOrderFetchRequest request) {
        if (request.updatedFrom() != null) {
            query.put("LastUpdatedAfter", PlatformJson.toIsoUtcQueryTimestamp(request.updatedFrom()));
            return;
        }
        LocalDateTime anchor = request.updatedTo() == null
                ? LocalDateTime.now(ZoneOffset.UTC) : request.updatedTo();
        LocalDateTime createdAfter = anchor.minusDays(DEFAULT_BACKFILL_DAYS);
        query.put("CreatedAfter", PlatformJson.toIsoUtcQueryTimestamp(createdAfter));
    }

    /**
     * 解出当前页的订单数组；探测不到任何候选键时返回空列表（视为该页没有订单）。
     */
    static List<Map<String, Object>> ordersIn(String jsonBody) {
        return PlatformJson.objects(firstListNode(rootOf(jsonBody), ORDER_ARRAY_PATHS));
    }

    /**
     * 2xx 空响应体在这里按「没有订单」处理而不是当成结构错误——连通性测试只关心调用是否被平台接受。
     */
    private static Map<String, Object> rootOf(String jsonBody) {
        return jsonBody == null || jsonBody.isBlank() ? Map.of() : PlatformJson.object(jsonBody);
    }

    private static Object firstListNode(Map<String, Object> root, String[][] paths) {
        for (String[] keys : paths) {
            Object value = PlatformJson.path(root, keys);
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    private static String firstTextAtPath(Map<String, Object> root, String[][] paths) {
        for (String[] keys : paths) {
            Object value = PlatformJson.path(root, keys);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstIdText(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.idText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeOrderNumber(String orderNumber) {
        return orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
    }

    /**
     * 记录已出行过的平台订单号；没有订单号的行不能被当成「已经见过」，交给导入侧的必填校验去拦。
     */
    private static boolean markSeen(Set<String> seenOrderIds, Object orderId) {
        return orderId == null || seenOrderIds.add(String.valueOf(orderId));
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
