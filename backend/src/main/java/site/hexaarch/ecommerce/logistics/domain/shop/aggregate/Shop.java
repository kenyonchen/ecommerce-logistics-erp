package site.hexaarch.ecommerce.logistics.domain.shop.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.shop.event.ShopAuthStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.shop.event.ShopCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 店铺聚合根：租户在某个第三方平台**某个站点**上的经营实体，粒度取站点（一站点一行）。
 * <p>
 * 它承载「卖家级」的东西——店铺身份与该站点的凭据；{@code platform_integrations} 只承载
 * 「我们跟平台对接的元信息」。二者分层的原因是归属层级本就是三层
 * （开发者应用 → 卖家授权 → 站点），压平在一张表里会让最底下的站点维度在模型中消失，
 * 于是多站点卖家的订单在拉回那一刻就混成一锅（见 ADR-0013 §1.1）。
 * </p>
 * <p>
 * 凭据字段（{@code apiKey/apiSecret/accessToken/refreshToken}）在聚合内是明文，
 * 加解密收敛在仓储边界；因此聚合与出参 DTO 都不得原样回传它们。
 * </p>
 *
 * @author kenyon
 */
@Getter
public class Shop extends BaseAggregateRoot<Object> {

    private String id;
    /**
     * 归属租户，必填。「空=全局」这种一列两语义不允许出现在租户资产表上。
     */
    private String tenantId;
    /**
     * 指向对接定义；自授权模式下可空
     */
    private String integrationId;
    /**
     * 平台标识（与 {@code orders.platform} 同宽同口径），是业务标签
     */
    private String platform;
    /**
     * 卖家自定的店铺编码，租户内唯一，是店铺的主身份。
     * <p>
     * 不用 {@code COALESCE(marketplaceId, externalShopId, storeUrl)} 推导：eBay / 速卖通 / Ozon 这类
     * 「凭据即店铺」的平台三个字段全为空，推导结果落在 NULL 上，而 MySQL 唯一键不约束 NULL，
     * 会同时得到「一个店都没有」与「无限个同名店」。ADR-0013 §2.3。
     * </p>
     */
    private String shopCode;
    private String shopName;
    /**
     * 站点标识（Amazon {@code ATVPDKIKX0DER}）；无站点概念的平台为空
     */
    private String marketplaceId;
    /**
     * 区域（Amazon {@code NA/EU/FE}），决定 API host
     */
    private String region;
    /**
     * 平台侧店铺/卖家账号标识（{@code shop_id}/{@code shop_cipher}/{@code user_id}）；非密钥
     */
    private String externalShopId;
    /**
     * 店铺地址（Shopify 域名、自建店铺 API 基址）
     */
    private String storeUrl;
    private String apiKey;
    private String apiSecret;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
    /**
     * 平台接口版本；为空则取对接定义的全局默认
     */
    private String apiVersion;
    private ShopAuthStatus authStatus;
    /**
     * 该店铺的发货回写策略，是「店铺级开关」；单级事实记在物流单的推送状态上，两层不合列
     */
    private PushBackPolicy pushBackPolicy;
    private boolean active;
    /**
     * 站点级订单同步水位
     */
    private LocalDateTime lastOrdersSyncedAt;
    private LocalDateTime lastProductsSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Shop() {
    }

    /**
     * 新建店铺。此时不含任何凭据，授权状态为 {@link ShopAuthStatus#PENDING}。
     *
     * @param integrationId 对接定义ID，可为空（自授权模式）
     */
    public static Shop create(String tenantId, String integrationId, String platform,
                              String shopCode, String shopName, String region, String marketplaceId) {
        require(tenantId, "租户ID不能为空");
        require(platform, "平台标识不能为空");
        require(shopCode, "店铺编码不能为空");
        require(shopName, "店铺名称不能为空");

        Shop shop = new Shop();
        shop.id = UUID.randomUUID().toString();
        shop.tenantId = tenantId.trim();
        shop.integrationId = blankToNull(integrationId);
        shop.platform = platform.trim().toUpperCase();
        shop.shopCode = shopCode.trim();
        shop.shopName = shopName.trim();
        shop.region = normalizeRegion(region);
        shop.marketplaceId = blankToNull(marketplaceId);
        shop.authStatus = ShopAuthStatus.PENDING;
        // 默认人工确认后再回写：新绑店铺尚未验证过凭据，自动推平台会把未核实的数据写进卖家账号
        shop.pushBackPolicy = PushBackPolicy.MANUAL;
        shop.active = true;
        shop.createdAt = LocalDateTime.now();
        shop.updatedAt = LocalDateTime.now();
        shop.registerDomainEvent(new ShopCreatedEvent(shop.id, shop.tenantId, shop.platform, shop.shopCode));
        return shop;
    }

    /**
     * 归一化站点标识（Amazon 的 NA/EU/FE、其他平台的 region）。
     * <p>
     * region 参与 API host 选择，大小写混写会让同一站点在库里出现两种取值、host 路由各走一半，
     * 所以在聚合写入时统一成大写；读取侧（外呼选 host）必须用同一方法或直接忽略大小写。
     * 抽成 public 是为了让应用服务的校验与存储用同一个归一化实现，避免「校验放过、入库变形」。
     */
    public static String normalizeRegion(String region) {
        String value = blankToNull(region);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /**
     * 从持久化状态还原，不注册事件。
     */
    public static Shop restore(String id, String tenantId, String integrationId, String platform,
                               String shopCode, String shopName, String marketplaceId, String region,
                               String externalShopId, String storeUrl, String apiKey, String apiSecret,
                               String accessToken, String refreshToken, LocalDateTime tokenExpiresAt,
                               String apiVersion, ShopAuthStatus authStatus, PushBackPolicy pushBackPolicy,
                               boolean active, LocalDateTime lastOrdersSyncedAt, LocalDateTime lastProductsSyncedAt,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        Shop shop = new Shop();
        shop.id = id;
        shop.tenantId = tenantId;
        shop.integrationId = integrationId;
        shop.platform = platform;
        shop.shopCode = shopCode;
        shop.shopName = shopName;
        shop.marketplaceId = marketplaceId;
        shop.region = region;
        shop.externalShopId = externalShopId;
        shop.storeUrl = storeUrl;
        shop.apiKey = apiKey;
        shop.apiSecret = apiSecret;
        shop.accessToken = accessToken;
        shop.refreshToken = refreshToken;
        shop.tokenExpiresAt = tokenExpiresAt;
        shop.apiVersion = apiVersion;
        shop.authStatus = authStatus == null ? ShopAuthStatus.PENDING : authStatus;
        shop.pushBackPolicy = pushBackPolicy == null ? PushBackPolicy.MANUAL : pushBackPolicy;
        shop.active = active;
        shop.lastOrdersSyncedAt = lastOrdersSyncedAt;
        shop.lastProductsSyncedAt = lastProductsSyncedAt;
        shop.createdAt = createdAt;
        shop.updatedAt = updatedAt;
        return shop;
    }

    /**
     * 改主数据（店铺名）。店铺编码是租户内的身份锚，也是订单归属的可读出处，改它等于换店，
     * 因此不在本方法开放——需要改编码请删店重建。
     */
    public void rename(String shopName) {
        require(shopName, "店铺名称不能为空");
        this.shopName = shopName.trim();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 启用/停用。停用中的店铺不应再被外呼，判定由 {@link #isCallable()} 收敛。
     */
    public void changeActive(boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 换发货回写策略。改它不重写任何历史单级状态——那层事实记在物流单上。
     */
    public void changePushBackPolicy(PushBackPolicy policy) {
        if (policy == null) {
            throw new ValidationException("发货回写策略不能为空");
        }
        this.pushBackPolicy = policy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 绑定对接定义。
     */
    public void bindIntegration(String integrationId) {
        this.integrationId = blankToNull(integrationId);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 写入卖家级凭据与站点身份（对应 {@code shop:credential_write} 权限点）。
     * <p>
     * 传 {@code null} 表示保留原值，传空串表示清除——外呼侧靠 {@code needsRefresh} 判断，
     * 清空即下一次调用重新换令牌。
     * </p>
     * <p>
     * <b>换绑凭据必定把授权状态打回 {@link ShopAuthStatus#PENDING}</b>：把已有店铺指向别的 token，
     * 就等于换了数据来源，此前的 ACTIVE 不再可信，必须重新过一次连通性测试。
     * </p>
     */
    public void bindCredentials(String apiKey, String apiSecret, String accessToken, String refreshToken,
                                LocalDateTime tokenExpiresAt, String marketplaceId, String region,
                                String externalShopId, String storeUrl, String apiVersion) {
        if (apiKey != null) {
            this.apiKey = blankToNull(apiKey);
        }
        if (apiSecret != null) {
            this.apiSecret = blankToNull(apiSecret);
        }
        if (accessToken != null) {
            this.accessToken = blankToNull(accessToken);
        }
        if (refreshToken != null) {
            this.refreshToken = blankToNull(refreshToken);
        }
        if (tokenExpiresAt != null) {
            this.tokenExpiresAt = tokenExpiresAt;
        }
        if (marketplaceId != null) {
            this.marketplaceId = blankToNull(marketplaceId);
        }
        if (region != null) {
            this.region = normalizeRegion(region);
        }
        if (externalShopId != null) {
            this.externalShopId = blankToNull(externalShopId);
        }
        if (storeUrl != null) {
            this.storeUrl = blankToNull(storeUrl);
        }
        if (apiVersion != null) {
            this.apiVersion = blankToNull(apiVersion);
        }
        this.updatedAt = LocalDateTime.now();
        changeAuthStatus(ShopAuthStatus.PENDING);
    }

    /**
     * 写入刷新后的令牌。语义与 {@code PlatformIntegration#applyToken} 一致：
     * {@code rotatedRefreshToken} 仅在平台确实轮换刷新令牌时传值，传 {@code null} 保留原值。
     */
    public void applyToken(String accessToken, LocalDateTime expiresAt, String rotatedRefreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            this.accessToken = accessToken.trim();
        }
        if (expiresAt != null) {
            this.tokenExpiresAt = expiresAt;
        }
        if (rotatedRefreshToken != null && !rotatedRefreshToken.isBlank()) {
            this.refreshToken = rotatedRefreshToken.trim();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void markAuthActive() {
        changeAuthStatus(ShopAuthStatus.ACTIVE);
    }

    public void markAuthError() {
        changeAuthStatus(ShopAuthStatus.ERROR);
    }

    public void markAuthExpired() {
        changeAuthStatus(ShopAuthStatus.EXPIRED);
    }

    /**
     * 是否可发起外呼：停用中的店铺一律不可，避免「界面看着停用、后台仍在拉单」。
     */
    public boolean isCallable() {
        return active;
    }

    public void markOrdersSyncedAt() {
        this.lastOrdersSyncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markProductsSyncedAt() {
        this.lastProductsSyncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 是否已配置凭据（供出参的 {@code credentialConfigured} 位与「未绑凭据」前置校验使用）。
     */
    public boolean hasAnyCredential() {
        return present(apiKey) || present(accessToken) || present(refreshToken);
    }

    private void changeAuthStatus(ShopAuthStatus target) {
        if (this.authStatus == target) {
            return;
        }
        this.authStatus = target;
        this.updatedAt = LocalDateTime.now();
        registerDomainEvent(new ShopAuthStatusChangedEvent(this.id, this.tenantId, target));
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
