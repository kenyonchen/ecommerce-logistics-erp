package site.hexaarch.ecommerce.logistics.domain.platform.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.platform.event.PlatformIntegrationCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.event.PlatformIntegrationStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 平台集成聚合根
 *
 * @author kenyon
 */
@Getter
public class PlatformIntegration extends BaseAggregateRoot {

    private UUID id;
    private String tenantId;
    private PlatformName platformName;
    /**
     * 对接协议；为空时按 {@code CUSTOM_SHOP}（通用自建店铺 HTTP 通道）处理
     */
    private PlatformAuthType authType;
    private String apiKey;
    private String apiSecret;
    /**
     * 访问令牌（Shopify 的 {@code shpat_} 令牌）；仓储层加密落库
     */
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
    /**
     * 平台接口版本（Shopify 形如 {@code 2026-07}），为空则取全局配置
     */
    private String apiVersion;
    private String storeUrl;
    /**
     * 平台区域/站点，用于选择 API host 与站点口径：Amazon 的 {@code NA/EU/FE}、Shopee 与 TikTok 的站点码。
     * 通用自建店铺协议不使用该字段。
     */
    private String region;
    /**
     * 平台侧的店铺/卖家账号标识：Shopee 的 {@code shop_id}、TikTok Shop 的 {@code shop_id}、
     * Mercado Libre 的 {@code user_id}。它不是密钥，但多数接口必填。
     */
    private String externalShopId;
    /**
     * 逗号分隔的市场标识：Amazon SP-API 的 {@code marketplaceIds}（如 {@code ATVPDKIKX0DER}）、
     * eBay 的 {@code marketplace_id}（如 {@code EBAY_US}）。
     */
    private String marketplaceIds;
    private PlatformStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 最近一次商品同步时间
     */
    private LocalDateTime lastProductsSyncedAt;
    /**
     * 最近一次订单同步时间
     */
    private LocalDateTime lastOrdersSyncedAt;

    private PlatformIntegration() {
        // Private constructor for builder
    }

    public static Builder builder() {
        return new Builder();
    }

    public void activate() {
        if (this.status != PlatformStatus.ACTIVE) {
            this.status = PlatformStatus.ACTIVE;
            this.updatedAt = LocalDateTime.now();
            registerDomainEvent(new PlatformIntegrationStatusChangedEvent(this.id, this.status));
        }
    }

    public void deactivate() {
        if (this.status == PlatformStatus.ACTIVE) {
            this.status = PlatformStatus.INACTIVE;
            this.updatedAt = LocalDateTime.now();
            registerDomainEvent(new PlatformIntegrationStatusChangedEvent(this.id, this.status));
        }
    }

    /**
     * 连通性测试或同步调用失败时标记为 {@code ERROR}（BR 状态机：ACTIVE/ERROR 转换，需人工介入）。
     */
    public void markError() {
        if (this.status != PlatformStatus.ERROR) {
            this.status = PlatformStatus.ERROR;
            this.updatedAt = LocalDateTime.now();
            registerDomainEvent(new PlatformIntegrationStatusChangedEvent(this.id, this.status));
        }
    }

    /**
     * 标记最近一次商品同步时间
     */
    public void markProductsSyncedAt() {
        this.lastProductsSyncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记最近一次订单同步时间
     */
    public void markOrdersSyncedAt() {
        this.lastOrdersSyncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 写入刷新后的访问令牌。
     * <p>
     * {@code rotatedRefreshToken} 只在平台确实轮换刷新令牌时传值（如 Shopee），传 {@code null} 表示保留原值。
     * 令牌由本方法落在聚合上后，必须经仓储 {@code save} 才会加密持久化——否则会退回到每次外呼都重新刷新，
     * 既浪费平台配额，也可能触发「刷新令牌一次性」类平台的作废。
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

    public void create() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PlatformStatus.INACTIVE;
        registerDomainEvent(new PlatformIntegrationCreatedEvent(this.id, this.tenantId, this.platformName));
    }

    public static class Builder {
        private final PlatformIntegration platformIntegration = new PlatformIntegration();

        public Builder id(UUID id) {
            platformIntegration.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            platformIntegration.tenantId = tenantId;
            return this;
        }

        public Builder platformName(PlatformName platformName) {
            platformIntegration.platformName = platformName;
            return this;
        }

        public Builder authType(PlatformAuthType authType) {
            platformIntegration.authType = authType;
            return this;
        }

        public Builder accessToken(String accessToken) {
            platformIntegration.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            platformIntegration.refreshToken = refreshToken;
            return this;
        }

        public Builder tokenExpiresAt(LocalDateTime tokenExpiresAt) {
            platformIntegration.tokenExpiresAt = tokenExpiresAt;
            return this;
        }

        public Builder apiVersion(String apiVersion) {
            platformIntegration.apiVersion = apiVersion;
            return this;
        }

        public Builder apiKey(String apiKey) {
            platformIntegration.apiKey = apiKey;
            return this;
        }

        public Builder apiSecret(String apiSecret) {
            platformIntegration.apiSecret = apiSecret;
            return this;
        }

        public Builder storeUrl(String storeUrl) {
            platformIntegration.storeUrl = storeUrl;
            return this;
        }

        public Builder region(String region) {
            platformIntegration.region = region;
            return this;
        }

        public Builder externalShopId(String externalShopId) {
            platformIntegration.externalShopId = externalShopId;
            return this;
        }

        public Builder marketplaceIds(String marketplaceIds) {
            platformIntegration.marketplaceIds = marketplaceIds;
            return this;
        }

        public Builder status(PlatformStatus status) {
            platformIntegration.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            platformIntegration.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            platformIntegration.updatedAt = updatedAt;
            return this;
        }

        public Builder lastProductsSyncedAt(LocalDateTime lastProductsSyncedAt) {
            platformIntegration.lastProductsSyncedAt = lastProductsSyncedAt;
            return this;
        }

        public Builder lastOrdersSyncedAt(LocalDateTime lastOrdersSyncedAt) {
            platformIntegration.lastOrdersSyncedAt = lastOrdersSyncedAt;
            return this;
        }

        public PlatformIntegration build() {
            return platformIntegration;
        }
    }
}
