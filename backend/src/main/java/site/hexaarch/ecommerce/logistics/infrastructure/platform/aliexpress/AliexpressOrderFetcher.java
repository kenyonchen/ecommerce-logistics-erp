package site.hexaarch.ecommerce.logistics.infrastructure.platform.aliexpress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 速卖通订单拉取适配器：{@code aliexpress.trade.seller.orderlist.get}（§5.2）。
 * <p>
 * 入参对象 {@code param_aeop_order_query} 携带
 * {@code create_date_start/create_date_end} 或 {@code modified_date_start/modified_date_end}
 * 与<b>必填、从 1 起</b>的 {@code current_page}/{@code page_size}（§5.2 特别指出这一点与 TOP 通用文档说的 0 起不同）；
 * 翻页由响应里的 {@code total_page} 决定。时间字段格式沿用 §5.1 核实过的 {@code yyyy-MM-dd HH:mm:ss}
 * （§5.2 记了字段<b>名</b>未记值格式，取值待实测，ISS-065）。
 * <p>
 * <b>响应里的订单对象字段名没有官方出处可依</b>：§5.2 核实了方法名、入参对象与信封，没核实 {@code …_response}
 * 内每单的键。因此单号/买家/金额/收件地址/明细一律按候选键探测，探不到就留空而不是断言某种形状；
 * 未被核实的候选键清单见本类底部注释与交付报告，全部归 ISS-065。
 * <p>
 * 输出行键对齐 {@code OrderImportTargetField} 别名表，与 {@code AmazonOrderFetcher}/{@code OzonOrderFetcher}
 * 同一套中立形状，导入侧字段映射与必填校验可复用。
 * <p>
 * 单订单详情增强（{@code aliexpress.trade.trade.get.single.result}，§5.2）<b>刻意不做</b>：§5.2 只记到方法名，
 * 没记它的入参对象名；在没有可引用的参数名之前，猜一个对象键去逐单回取等于凭空造 TOP 参数（硬规则禁止），
 * 因此列表拿不到的收件人/明细就留空，由导入侧必填校验去报这一行。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AliexpressOrderFetcher implements PlatformOrderFetcher {

    private static final int DEFAULT_TOTAL_LIMIT = 100;

    /**
     * 没有时间界时的回溯窗口（天），沿用 Amazon/Ozon 适配器的同名口径
     */
    private static final int DEFAULT_BACKFILL_DAYS = 30;

    /**
     * §5.2 记 {@code page_size} 必填但未给平台上限；这里是<b>本仓库自用的单页安全上限</b>（非平台核实值），
     * 只为避免一次请求塞进过大 body，实测平台上限后再调整（ISS-065）。
     */
    static final int LOCAL_MAX_PAGE_SIZE = 100;

    static final int DEFAULT_PAGE_SIZE = 40;

    /**
     * 订单数组在成功信封内的候选位置：§5.2 未核实其命名，只探测不假定。TOP 常把数组再包一层
     * （如 {@code {"orders":{"order":[…]}}}），故每个候选键都同时试「直接是数组」与「对象里首个数组」。
     */
    private static final String[] ORDER_ARRAY_KEYS = {
            "orders", "list", "trade_overviews", "trade_overview_list", "tobuy_order_list", "result"
    };

    private static final String[] ORDER_ID_KEYS = {"orderId", "order_id", "tradeId", "trade_id", "id"};
    private static final String[] CUSTOMER_NAME_KEYS = {
            "buyerFullName", "buyer_full_name", "customerName", "customer_name", "buyerName", "username"
    };
    private static final String[] EMAIL_KEYS = {"email", "buyerEmail", "buyer_email", "customerEmail"};
    private static final String[] STATUS_KEYS = {"orderStatus", "order_status", "status"};
    private static final String[] TIME_KEYS = {"gmtCreate", "create_time", "tradeCreateTime", "orderTime"};
    private static final String[] CURRENCY_KEYS = {"currency", "currencyCode", "payCurrency", "paymentCurrency"};
    private static final String[] TOTAL_KEYS = {
            "totalAmount", "total_amount", "orderAmount", "paymentAmount", "sumTotalAmount"
    };
    private static final String[] PAYMENT_METHOD_KEYS = {"paymentType", "payment_type", "payType", "paymentMethod"};
    private static final String[] PAYMENT_STATUS_KEYS = {"paymentStatus", "payment_status", "payStatus"};
    private static final String[] LINE_ITEM_KEYS = {
            "orderItems", "order_items", "products", "itemList", "tradeItemList"
    };
    /**
     * 地址既可能是嵌套对象（详情接口形态），也可能在列表里以 {@code receiverXxx} 平铺；两处都探。
     */
    private static final String[] ADDRESS_OBJECT_KEYS = {"shippingAddress", "receiverAddressInfo", "address"};
    private static final String[] RECIPIENT_KEYS = {"name", "fullName", "receiverName", "contactPerson"};
    private static final String[] PHONE_KEYS = {"phone", "mobile", "telephone", "receiverPhone", "mobileNo"};
    private static final String[] COUNTRY_KEYS = {"country", "countryName", "receiverCountry", "shipToCountry", "shippingTo"};
    private static final String[] PROVINCE_KEYS = {"province", "provinceName", "state", "receiverState"};
    private static final String[] CITY_KEYS = {"city", "cityName", "receiverCity"};
    private static final String[] ADDRESS_LINE_KEYS = {"address", "street", "detailAddress", "receiverAddress", "addressLine1"};
    private static final String[] ZIP_KEYS = {"zip", "zipCode", "postalCode", "postCode", "receiverPostCode"};

    private final AliexpressTopClient client;

    public AliexpressOrderFetcher(AliexpressTopClient client) {
        this.client = client;
    }

    @Override
    public String type() {
        return PlatformAuthType.ALIEXPRESS.name();
    }

    /**
     * TOP 必须要 app_key/app_secret/session 才能签名并鉴权，单参重载无法凭空构造凭据。
     */
    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        throw new BusinessException("速卖通订单适配器需要凭据上下文(apiKey=app_key、apiSecret=app_secret、"
                + "accessToken=session)，请经适配器注册表调用", "PLATFORM_CREDENTIAL_REQUIRED");
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        int totalLimit = request.limit() == null || request.limit() <= 0 ? DEFAULT_TOTAL_LIMIT : request.limit();
        int pageSize = clampPageSize(request.limit());
        int currentPage = request.page() == null || request.page() < 1 ? 1 : request.page();
        String wantedOrderId = normalizeOrderNumber(request.orderNumber());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();
        Set<String> previousPageIds = null;
        int pages = 0;
        while (pages < client.maxPages() && rows.size() < totalLimit) {
            Map<String, Object> envelope = client.execute(credential, AliexpressTopClient.ORDER_LIST_METHOD,
                    businessParams(request, currentPage, pageSize));
            List<Map<String, Object>> orders = ordersIn(envelope);
            if (orders.isEmpty()) {
                break;
            }
            Set<String> pageIds = new LinkedHashSet<>();
            for (Map<String, Object> order : orders) {
                String orderId = firstId(order, ORDER_ID_KEYS);
                if (orderId != null) {
                    pageIds.add(orderId);
                }
            }
            // 平台忽略 current_page、连续返回同一页时收手，否则会在页数上限上空转（不推进的页）
            if (!pageIds.isEmpty() && pageIds.equals(previousPageIds)) {
                break;
            }
            previousPageIds = pageIds;

            for (Map<String, Object> order : orders) {
                String orderId = firstId(order, ORDER_ID_KEYS);
                if (orderId == null || !seenOrderIds.add(orderId)) {
                    // 无单号无法去重，交给导入侧必填校验去拦；跨页重复的单不能重复出行
                    continue;
                }
                if (wantedOrderId != null && !wantedOrderId.equals(orderId)) {
                    continue;
                }
                rows.add(toRow(order));
                if (rows.size() >= totalLimit) {
                    break;
                }
            }
            pages++;
            Integer totalPage = totalPage(envelope);
            if (totalPage != null && currentPage >= totalPage) {
                break;
            }
            currentPage++;
        }
        if (pages >= client.maxPages()) {
            log.warn("[AliExpress] 订单拉取达到翻页上限 {}，可能有后续页未取回 platformOrders={}",
                    client.maxPages(), rows.size());
        }
        log.info("[AliExpress] 订单拉取完成 pages={} rows={}", pages, rows.size());
        return rows;
    }

    /**
     * 把速卖通订单对象映射为导入侧的中立行；所有源键都是候选探测（§5.2 未核实订单对象结构），探不到就留空。
     */
    Map<String, Object> toRow(Map<String, Object> order) {
        Map<String, Object> row = PlatformJson.linkedRow();
        Map<String, Object> address = probeNested(order, ADDRESS_OBJECT_KEYS);

        row.put("orderId", firstId(order, ORDER_ID_KEYS));
        row.put("customerName", firstText(order, CUSTOMER_NAME_KEYS));
        row.put("email", firstText(order, EMAIL_KEYS));
        row.put("orderStatus", bridgeStatus(firstText(order, STATUS_KEYS)));
        row.put("orderTime", readTime(order, TIME_KEYS));
        row.put("currency", firstText(order, CURRENCY_KEYS));
        row.put("totalAmount", firstDecimal(order, TOTAL_KEYS));
        row.put("paymentMethod", firstText(order, PAYMENT_METHOD_KEYS));
        row.put("paymentStatus", firstText(order, PAYMENT_STATUS_KEYS));
        row.put("recipientName", firstNonBlank(firstText(address, RECIPIENT_KEYS), firstText(order, RECIPIENT_KEYS),
                firstText(order, CUSTOMER_NAME_KEYS)));
        row.put("phoneNumber", firstNonBlank(firstText(address, PHONE_KEYS), firstText(order, PHONE_KEYS)));
        row.put("country", firstNonBlank(firstText(address, COUNTRY_KEYS), firstText(order, COUNTRY_KEYS)));
        row.put("province", firstNonBlank(firstText(address, PROVINCE_KEYS), firstText(order, PROVINCE_KEYS)));
        row.put("city", firstNonBlank(firstText(address, CITY_KEYS), firstText(order, CITY_KEYS)));
        row.put("addressLine1", firstNonBlank(firstText(address, ADDRESS_LINE_KEYS), firstText(order, ADDRESS_LINE_KEYS)));
        row.put("zipCode", firstNonBlank(firstText(address, ZIP_KEYS), firstText(order, ZIP_KEYS)));
        // 明细数组原样透传：元素结构未被 §5.2 核实，实测后在此处一次性对齐
        row.put("lineItems", firstObjects(order, LINE_ITEM_KEYS));
        return row;
    }

    /**
     * 状态桥接：速卖通订单状态是 TOP 自有枚举，ERP 侧只认 {@code OrderStatus} 的六个码。
     * <b>下表取值来自速卖通公开的状态词表，§5 未逐个核实（ISS-065，以实测为准）</b>；
     * 比较前把下划线归一为空格并小写，再按完整取值匹配——{@code WAIT_SELLER_SEND_GOODS}（待卖家发货）与
     * {@code SELLER_SEND_GOODS}（已发货）互为字面前缀，用「包含」会判错，故必须整串相等。
     * <pre>
     *   wait buyer pay / payment pending / order in fund processing      -&gt; pending     未付款/资金处理中
     *   wait seller send goods / seller part send goods                  -&gt; processing  已付款待(全部)发货
     *   seller send goods / wait buyer confirm goods / shipped           -&gt; shipped     已在途
     *   order finished / trade finished / finish / loan made             -&gt; completed   交易完成
     *   in cancel / cancel order                                         -&gt; cancelled
     *   其他                                                              -&gt; 原样透出
     * </pre>
     * 认不出的一律原样透出而不是硬塞 {@code pending}：导入侧 {@code toOrderStatus} 会兜底成待处理，
     * 但保留原文才能让人看见平台侧出现了新的状态值。
     */
    static String bridgeStatus(String aliexpressStatus) {
        if (aliexpressStatus == null || aliexpressStatus.isBlank()) {
            return null;
        }
        String value = aliexpressStatus.trim();
        String normalized = value.toLowerCase().replace('_', ' ');
        return switch (normalized) {
            case "wait buyer pay", "payment pending", "order in fund processing" -> "pending";
            case "wait seller send goods", "seller part send goods" -> "processing";
            case "seller send goods", "wait buyer confirm goods", "shipped" -> "shipped";
            case "order finished", "trade finished", "finish", "loan made" -> "completed";
            case "in cancel", "cancel order" -> "cancelled";
            default -> value;
        };
    }

    /**
     * 业务参数：{@code param_aeop_order_query} 的值是 JSON 串（§5.2 的入参对象）。
     * 有 {@code updatedFrom/updatedTo} 走 {@code modified_date_*}（增量同步），否则走 {@code create_date_*} 并给
     * 30 天回溯窗口（两个界都没有时窗口兜底，避免无界全量拉取放大分页成本，口径同 Amazon/Ozon）。
     * {@code current_page}/{@code page_size} 必填且从 1 起（§5.2）。
     */
    private static Map<String, String> businessParams(PlatformOrderFetchRequest request, int currentPage, int pageSize) {
        boolean incremental = request.updatedFrom() != null || request.updatedTo() != null;
        LocalDateTime to = request.updatedTo() != null ? request.updatedTo() : LocalDateTime.now();
        LocalDateTime from = request.updatedFrom() != null
                ? request.updatedFrom() : to.minusDays(DEFAULT_BACKFILL_DAYS);
        Map<String, Object> query = new LinkedHashMap<>();
        if (incremental) {
            query.put("modified_date_start", from.format(AliexpressTopClient.TOP_DATE_FORMAT));
            query.put("modified_date_end", to.format(AliexpressTopClient.TOP_DATE_FORMAT));
        } else {
            query.put("create_date_start", from.format(AliexpressTopClient.TOP_DATE_FORMAT));
            query.put("create_date_end", to.format(AliexpressTopClient.TOP_DATE_FORMAT));
        }
        query.put("current_page", currentPage);
        query.put("page_size", pageSize);
        Map<String, String> business = new LinkedHashMap<>();
        business.put("param_aeop_order_query", PlatformJson.write(query));
        return business;
    }

    /**
     * 解出成功信封里当前页的订单数组（连通性测试也复用）。
     */
    static List<Map<String, Object>> ordersIn(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return List.of();
        }
        for (String key : ORDER_ARRAY_KEYS) {
            List<Map<String, Object>> rows = extractList(envelope.get(key));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        // 兜底：候选键都没命中时取信封里第一个数组（订单列表方法的唯一数组就是订单本身）
        for (Object value : envelope.values()) {
            List<Map<String, Object>> rows = extractList(value);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    /**
     * {@code total_page} 是 §5.2 的翻页依据；缺失时返回 {@code null}，调用方据此只取当前一页而不误判还有多页。
     */
    private static Integer totalPage(Map<String, Object> envelope) {
        Integer value = PlatformJson.integer(envelope, "total_page");
        return value != null ? value : PlatformJson.integer(envelope, "totalPage");
    }

    /**
     * 值既可能是数组，也可能是 TOP 常见的「对象包一层数组」（{@code {orders:{order:[…]}}}）；两种都取出内层数组。
     */
    private static List<Map<String, Object>> extractList(Object value) {
        if (value instanceof List<?> list) {
            return PlatformJson.objects(list);
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : asMap(map).values()) {
                if (nested instanceof List<?>) {
                    return PlatformJson.objects(nested);
                }
            }
        }
        return List.of();
    }

    /**
     * 单页条数：没给就取本地默认值，给了夹到本仓库自用的安全上限（§5.2 未给平台上限，见常量注释）。
     */
    static int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, LOCAL_MAX_PAGE_SIZE);
    }

    private static String normalizeOrderNumber(String orderNumber) {
        return orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
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

    private static String firstDecimal(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.decimalText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstId(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            String value = PlatformJson.idText(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> firstObjects(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            List<Map<String, Object>> rows = extractList(node.get(key));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    private static Map<String, Object> probeNested(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            if (node.get(key) instanceof Map<?, ?> map) {
                return asMap(map);
            }
        }
        return Map.of();
    }

    /**
     * 时间：TOP 的日期字段可能是 {@code yyyy-MM-dd HH:mm:ss} 串、epoch 秒/毫秒或 ISO 串，形态未被 §5.2 核实；
     * 三种都试并归一为导入侧格式，认不出就留空而不是把 {@code toString} 丢给导入侧。
     */
    private static String readTime(Map<String, Object> node, String[] keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                String converted = PlatformJson.epochSecondsToImportDateTime(value);
                if (converted != null) {
                    return converted;
                }
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return text;
            }
            if (text.matches("\\d{10,13}")) {
                String converted = PlatformJson.epochSecondsToImportDateTime(text);
                if (converted != null) {
                    return converted;
                }
                continue;
            }
            try {
                String converted = PlatformJson.toImportDateTime(text);
                if (converted != null) {
                    return converted;
                }
            } catch (BusinessException ignored) {
                // 形态未知，留空
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
