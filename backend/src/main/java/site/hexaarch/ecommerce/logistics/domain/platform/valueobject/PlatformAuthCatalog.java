package site.hexaarch.ecommerce.logistics.domain.platform.valueobject;

import java.util.List;

/**
 * 各对接协议的凭据要求与官方文档索引。
 * <p>
 * 这张表是「配置界面上该填哪几把钥匙」的唯一口径来源，同时也是本轮接口对接的交付边界声明：
 * {@code verified=false} 的行表示该协议的签名规则或接口名无法从公开可读的官方页面核实，
 * 因此<b>没有</b>对应适配器——不是遗漏，而是不能凭想象编造端点和签名串。
 * <p>
 * {@code docUrls} 只收录本次核实过程中实际读取过的官方地址；只拿到域名的就只写域名，
 * 不补一个「看起来对」的深层路径。
 *
 * @author kenyon
 */
public final class PlatformAuthCatalog {

    private static final List<PlatformAuthSpec> SPECS = List.of(
            new PlatformAuthSpec(
                    PlatformAuthType.CUSTOM_SHOP,
                    "通用自建店铺",
                    List.of("storeUrl", "apiKey", "apiSecret"),
                    List.of(),
                    false,
                    List.of(),
                    "按仓库自身的约定：`GET {storeUrl}/{resource}` + `X-API-Key`/`X-API-Secret` 头。"
                            + "apiKey/apiSecret 由店铺服务端自行发放。",
                    List.of(),
                    true,
                    "对接落地前的默认通道，历史数据全在这里；也是文件上传导入之外的兜底协议。"),
            new PlatformAuthSpec(
                    PlatformAuthType.SHOPIFY,
                    "Shopify",
                    List.of("storeUrl", "accessToken"),
                    List.of("apiVersion"),
                    false,
                    List.of(),
                    "店铺后台 Settings → Apps and sales channels → Develop apps → Admin API integration "
                            + "生成 `shpat_` 开头的客户端访问令牌，勾选 write_orders/read_orders/read_products。"
                            + "storeUrl 填 API 域名（https://店铺.myshopify.com），不是 admin.shopify.com。",
                    List.of("https://shopify.dev/docs/api/usage",
                    "https://shopify.dev/docs/api/admin-rest"),
                    true,
                    "订单、商品拉取 + 发货回写 + 连通性测试已实现。分页只认 Link 头的 page_info 游标；"
                            + "库存推送未实现（缺本地 SKU 到 inventory_item_id/location_id 的映射）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.MAGENTO,
                    "Magento / Adobe Commerce",
                    List.of("storeUrl", "apiKey", "apiSecret"),
                    List.of("region"),
                    false,
                    List.of("default", "website", "store"),
                    "Self-hosted 用 Admin Login：apiKey 填管理员用户名、apiSecret 填口令，"
                            + "适配器会 PUT /rest/V1/integration/admin/token 换 Bearer 令牌；"
                            + "Commerce Cloud/SaaS 不能走登录接口，须把集成访问令牌直接填进 accessToken。"
                            + "region 填 store_view_code（缺省 default）。",
                    List.of("https://developer.adobe.com/commerce/webapi/rest/use-rest/authentication",
                    "https://developer.adobe.com/commerce/webapi/rest/use-rest/performing-crud-orders",
                    "https://developer.adobe.com/commerce/webapi/rest/use-rest/calling-apis-with-search-parameters",
                    "https://developer.adobe.com/commerce/webapi/rest/use-rest/call-apis-with-administrative-resources"),
                    true,
                    "searchCriteria 过滤器里 field/value 必须各出现一次；条件之间是 AND；"
                    + "like 需自带通配符，「精确匹配」运算符公开文档未给出（ISS-065）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.AMAZON_SP_API,
                    "Amazon Selling Partner API",
                    List.of("apiKey", "apiSecret", "refreshToken", "marketplaceIds"),
                    List.of("region"),
                    true,
                    List.of("NA", "EU", "FE"),
                    "Seller Central → Partner Network → Selling Partner API 注册应用，拿到 LWA "
                    + "client_id/client_secret（填 apiKey/apiSecret）与 Self-authorization 生成的 "
                    + "refresh_token（填 refreshToken）。marketplaceIds 填售卖站点 ID（如 US 为 ATVPDKIKX0DER）。"
                            + "host 按 region 内置（sellingpartnerapi-na/eu/fe.amazon.com），storeUrl 不用填。",
                    List.of("https://developer-docs.amazon.com/sp-api/"),
                    true,
                    "令牌端点官方口径是 www.amazon.com/ap/oath/v1/token（另有 ru/in 变体），"
                    + "与部分历史实现写的 api.amazon.com/auth/o2/token 不同，已按官方页取值；"
                    + "沙箱路径是 /sandbox 前缀（ISS-065）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.EBAY_SELL,
                    "eBay Sell APIs",
                    List.of("apiKey", "apiSecret", "refreshToken"),
                    List.of("marketplaceIds", "storeUrl"),
                    true,
                    List.of(),
                    "developer.ebay.com 建 OAuth 应用拿 appid/certid（填 apiKey）与 secret（填 apiSecret）；"
                            + "用 authorization URL 走一次用户授权，把返回的 refresh_token 填进 refreshToken。"
                            + "scope 固定 https://api.ebay.com/oauth/api_scope/sell.fulfillment"
                            + " https://api.ebay.com/oauth/api_scope/sell.inventory。marketplaceIds 填 EBAY_US 等。",
                    List.of("https://developer.ebay.com/api-docs/sell/fulfillment/order-collections.html",
                    "https://developer.ebay.com/api-docs/sell/fulfillment/overview.html"),
                    true,
                    "订单列表 GET /sell/fulfillment/v1/order 只有 fieldGroups|filter|limit|offset|orderIds "
                    + "五个参数，旧的 getAllOrders 与 creation_date_range=/order_status= 独立参数都已废弃，"
                    + "全部过滤条件塞进单个 filter= 参数（creationdate:[ISO..ISO]、lastmodifieddate:、"
                    + "orderfulfillmentstatus:{...}），limit 上限 200、缺省 50，"
                    + "下一页在响应体 next/href 而不是 Next-Page 头；沙箱 host 是 apiz.sandbox.ebay.com。"
                    + "fulfillment 域没有商品列表，商品走 sell/inventory 的 GET /inventory_item"
                    + "（limit 1–200，offset 是页码）与 GET /offer（上限 100）。刷新令牌不轮换。"
                    + "商品列表需另开 sell.inventory scope，当前适配器只接 fulfillment 域（ISS-065）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.SHOPEE,
                    "Shopee Open Platform v2",
                    List.of("apiKey", "apiSecret", "refreshToken", "externalShopId"),
                    List.of("region", "storeUrl"),
                    true,
                    List.of("sandbox", "production"),
                    "open.shopee.com 建应用拿 partner_id（填 apiKey）与 partner_key/secret（填 apiSecret）；"
                            + "走一次卖家授权拿 access_token/refresh_token（两个都填上，" 
                            + "Shopee 的刷新令牌是一次性的，必须落库）。externalShopId 填 shop_id。"
                            + "host 内置 partner.shopeemobile.com，沙箱靠 x-env-type: sandbox 头切换。",
                    List.of("https://open.shopee.com/developer-guide/16",
                    "https://open.shopee.com/developer-guide/20",
                    "https://open.shopee.com/documents/v2/v2.order.get_order_list",
                    "https://open.shopee.com/documents/v2/v2.product.get_item_list",
                    "https://open.shopee.com/documents/v2/v2.logistics.ship_order"),
                    true,
                    "签名串顺序是 partner_id + path + timestamp（+ access_token + shop_id），"
                            + "hex 小写；商品列表在 product/ 不在 item/，发活在 logistics/ship_order。"),
            new PlatformAuthSpec(
                    PlatformAuthType.TIKTOK_SHOP,
                    "TikTok Shop Partner API",
                    List.of("apiKey", "apiSecret", "accessToken", "externalShopId"),
                    List.of("refreshToken", "storeUrl", "region"),
                    true,
                    List.of("global", "us"),
                    "partner.tiktokshop.com 建应用拿 app_key/app_secret（填 apiKey/apiSecret）；"
                            + "授权回调拿 code 换 access_token，并每 7 天用 refresh_token 续期"
                            + "（TikTok 会轮换刷新令牌，必须回写）。externalShopId 填 shop_cipher。"
                            + "授权页非美国是 services.tiktokshop.com/open/authorize，美国是 services.us.tiktokshop.com。",
                    List.of("https://developers.tiktok.com/"),
                    false,
                    "签名与 OAuth 已按公开文档核实；但公开文档描述的是 202212 代接口"
                            + "（POST /api/orders/search 等，平台自标 v1_deprecated），"
                            + "202309 代接口与 X-Tts-* 头规范要登录 Partner Center 才能读到，"
                            + "因此没有适配器（ISS-065，规范缺口见 docs/spec/platform-api-reference.md §10.1）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.ALIEXPRESS,
                    "全球速卖通（TOP 同源网关）",
                    List.of("apiKey", "apiSecret", "accessToken"),
                    List.of("storeUrl"),
                    true,
                    List.of(),
                    "open.aliexpress.com 入驻 ISV 拿 app_key/app_secret（填 apiKey/apiSecret）；"
                            + "卖家授权后拿 access_token 填 accessToken（TOP 系把它作为 session 参数带在公共参数里）。"
                            + "网关默认 gw.api.taobao.com/router/rest，可用 storeUrl 覆盖成海外 eco.taobao.com。",
                    List.of("https://developer.alibaba.com/docs/doc.htm?articleId=101617&docType=1",
                    "https://developer.alibaba.com/docs/api.htm?apiId=37898",
                    "https://developer.alibaba.com/docs/api.htm?apiId=128"),
                    true,
                    "订单列表用 aliexpress.trade.seller.orderlist.get（current_page/page_size 从 1 起）；"
                            + "公开可核实的商品列表只有联盟接口 aliexpress.traffic.product.search（需单独权限包），"
                            + "卖家自有商品列表方法要登录 open.aliexpress.com（ISS-065）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.MERCADO_LIBRE,
                    "Mercado Libre",
                    List.of("apiKey", "apiSecret", "refreshToken"),
                    List.of("externalShopId", "marketplaceIds", "storeUrl"),
                    true,
                    List.of(),
                    "developers.mercadolibre.com 建应用拿 client_id/client_secret（填 apiKey/apiSecret）；"
                            + "走一次用户授权拿 refresh_token（填 refreshToken）。"
                            + "刷新令牌只可用一次且会轮换，务必落库。externalShopId 填卖家 user_id。"
                            + "scope 缺 offline_access 时不返回 refresh_token（需带凭据实测确认）。",
                    List.of("https://developers.mercadolibre.com/com/documentation/pages/67104ea1f33d8550f9777fb3",
                    "https://developers.mercadolibre.com/com/documentation/pages/67104e1a65c70700072d9015"),
                    true,
                    "CBT 跨境卖家订单走 /marketplace/orders/search（seller.id 作过滤、"
                            + "last_updated.gte/lt 是 ISO-8601 带偏移字符串）；"
                            + "国内版 /orders/search?seller_id= 形式正文未核实到（ISS-065）。"),
            new PlatformAuthSpec(
                    PlatformAuthType.OZON,
                    "Ozon Seller API",
                    List.of("apiKey", "apiSecret"),
                    List.of("storeUrl"),
                    true,
                    List.of(),
                    "Ozon 卖家后台 → Settings → API keys 生成，Client-Id 填 apiKey、Api-Key 填 apiSecret。"
                            + "没有 OAuth、没有刷新、没有签名。host 只认 https://api-seller.ozon.ru（强制 https），"
                            + "可用 storeUrl 覆盖。",
                    List.of("https://docs.ozon.ru/api/seller/"),
                    true,
                    "文档站有反爬（403），但关键端点已从官方文档正文核实：列表接口不返回明细，"
                            + "必须逐单再调 /v2/posting/fbs/get；时间字段必须带时区偏移；"
                            + "没有商品列表接口，商品要 /v3/product/list + /v4/product/info/attributes 两步。"),
            new PlatformAuthSpec(
                    PlatformAuthType.TEMU,
                    "Temu 开放平台",
                    List.of("apiKey", "apiSecret", "accessToken"),
                    List.of("region", "storeUrl"),
                    true,
                    List.of("cn", "us", "eu", "global"),
                    "seller.kuajingmaihuo.com / partner.kuajingmaihuo.com 入驻开发者拿 app_key/app_secret 与 access_token。",
                    List.of("https://seller.kuajingmaihuo.com/sop/view/634117628601810731",
                    "https://partner.kuajingmaihuo.com/document"),
                    false,
                    "网关与请求信封已从官方端点实测核实（POST /openapi/router，body 里带 type/app_key/"
                            + "timestamp/access_token/sign，响应是 success/requestId/errorCode/errorMsg），"
                            + "但签名算法与各接口 type 只存在于需登录卖家账号的文档站，"
                            + "公开抓取全部落空，因此没有适配器（ISS-065）。"));

    private PlatformAuthCatalog() {
    }

    public static List<PlatformAuthSpec> all() {
        return SPECS;
    }

    public static PlatformAuthSpec of(PlatformAuthType type) {
        for (PlatformAuthSpec spec : SPECS) {
            if (spec.type() == type) {
                return spec;
            }
        }
        return of(PlatformAuthType.CUSTOM_SHOP);
    }
}
