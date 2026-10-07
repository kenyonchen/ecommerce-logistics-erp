package site.hexaarch.ecommerce.logistics.domain.platform.valueobject;

/**
 * 平台对接协议（认证方式 + 接口形状）类型。
 * <p>
 * 与 {@link PlatformName} 的区别：{@code PlatformName} 是业务侧的平台标识（店铺归属、报表口径），
 * 本枚举决定**用哪套适配器去调**。同一个 SHOPIFY 店铺可能走 {@code CUSTOM_SHOP}（自建网关代理），
 * 历史数据全部落在 {@code CUSTOM_SHOP}，因此它也是默认值，不改变既有行为。
 *
 * @author kenyon
 */
public enum PlatformAuthType {
    /**
     * 通用自建店铺协议：{@code GET {storeUrl}/{resource}} + {@code X-API-Key}/{@code X-API-Secret} 头。
     * 即对接落地前的默认通道，对应 {@code HttpPlatform*Fetcher}。
     */
    CUSTOM_SHOP,
    /**
     * Shopify Admin REST API：{@code X-Shopify-Access-Token} 头 + {@code Link} 头 {@code page_info} 游标分页。
     */
    SHOPIFY,
    /**
     * Adobe Commerce / Magento 2 的 REST Admin API：{@code /rest/V1} + Bearer 集成令牌。
     */
    MAGENTO,
    /**
     * Amazon Selling Partner API：LWA（Login with Amazon）刷新令牌换 1 小时 Bearer 令牌，
     * 三个区域各自独立 host（NA / EU / FE）。
     */
    AMAZON_SP_API,
    /**
     * eBay Sell APIs（Fulfillment / Inventory）：OAuth2 用户授权 + Bearer 令牌，全球共用一个 host。
     */
    EBAY_SELL,
    /**
     * Temu 开放平台：网关式协议，公共参数 + {@code app_secret} 参与请求签名。
     */
    TEMU,
    /**
     * TikTok Shop 开放平台：OAuth2 授权 + 每请求 HMAC-SHA256 签名（{@code X-Tts-*} 头）。
     */
    TIKTOK_SHOP,
    /**
     * 速卖通（AliExpress）开放平台，与淘宝 TOP 网关同源：{@code method} 式接口名 + md5/hmac 签名 + {@code session} 会话密钥。
     */
    ALIEXPRESS,
    /**
     * Shopee 开放平台：OAuth2 + 每请求 HMAC-SHA256 签名，{@code partner_id}/{@code shop_id} 双身份。
     */
    SHOPEE,
    /**
     * Mercado Libre：OAuth2 用户授权（{@code offline_access} 才有刷新令牌）+ Bearer 令牌。
     */
    MERCADO_LIBRE,
    /**
     * Ozon：静态凭据头 {@code Client-Id} + {@code Api-Key}，无签名、无 OAuth 刷新。
     */
    OZON;

    /**
     * 适配器注册表使用的键；与 {@code Platform*Fetcher#type()} 的返回值对齐。
     */
    public String registryKey() {
        return name();
    }

    /**
     * 是否需要「短期访问令牌 + 刷新令牌」的 OAuth 形态凭据。
     * <p>
     * 为 {@code true} 时外呼前要过 {@code PlatformTokenProvider} 检查到期时间并按需刷新；
     * 为 {@code false} 的协议（静态密钥/签名式）直接用配置的凭据签名，不做令牌刷新。
     */
    public boolean oauthTokenBased() {
        return this == AMAZON_SP_API || this == EBAY_SELL || this == TIKTOK_SHOP
                || this == SHOPEE || this == MERCADO_LIBRE;
    }

    /**
     * 是否为「每个请求都要算签名」的网关式协议。
     */
    public boolean requestSigned() {
        return this == TEMU || this == TIKTOK_SHOP || this == ALIEXPRESS || this == SHOPEE;
    }

    public static PlatformAuthType parse(String text, PlatformAuthType fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
