package site.hexaarch.ecommerce.logistics.interfaces.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.shop.aggregate.Shop;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;

import java.time.LocalDateTime;

/**
 * 店铺出参 DTO。
 * <p>
 * <b>凭据纪律（ADR-0013 §4.5）：本类不含任何凭据明文或密文字段。</b>凭据只以
 * {@code apiKeyMasked} 与 {@code hasApiSecret/hasAccessToken/hasRefreshToken} 三个布尔位出现，
 * 与 {@code PlatformIntegrationDto} 同形。多出来的 {@code credentialConfigured} 位供列表页
 * 直接判断「这个店还没绑钥匙」，不必让前端猜三个布尔的或运算。
 * </p>
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "店铺（凭据以掩码与是否已配置标志返回）")
public class ShopDto {

    private String id;
    private String tenantId;
    @Schema(description = "关联的对接定义ID，自授权模式为空")
    private String integrationId;
    private String platform;
    @Schema(description = "租户内唯一的店铺编码（卖家自定，店铺主身份）")
    private String shopCode;
    private String shopName;
    @Schema(description = "站点标识，如 Amazon 的 ATVPDKIKX0DER；无站点概念的平台为空")
    private String marketplaceId;
    @Schema(description = "区域，如 Amazon 的 NA/EU/FE，决定 API host")
    private String region;
    @Schema(description = "平台侧店铺/卖家账号标识（非密钥）")
    private String externalShopId;
    @Schema(description = "店铺地址（Shopify 域名、自建店铺 API 基址）")
    private String storeUrl;
    private String apiVersion;
    @Schema(description = "API Key 掩码，永不返回完整值")
    private String apiKeyMasked;
    @Schema(description = "是否已配置 API Secret")
    private boolean hasApiSecret;
    @Schema(description = "是否已配置访问令牌")
    private boolean hasAccessToken;
    @Schema(description = "是否已配置刷新令牌（OAuth 协议需要）")
    private boolean hasRefreshToken;
    @Schema(description = "三类凭据是否至少配置了一个")
    private boolean credentialConfigured;
    @Schema(description = "令牌到期时间，非敏感")
    private LocalDateTime tokenExpiresAt;
    private ShopAuthStatus authStatus;
    @Schema(description = "发货回写策略：AUTO/MANUAL/OFF")
    private PushBackPolicy pushBackPolicy;
    private boolean active;
    @Schema(description = "站点级订单同步水位")
    private LocalDateTime lastOrdersSyncedAt;
    private LocalDateTime lastProductsSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShopDto from(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopDto dto = new ShopDto();
        dto.id = shop.getId();
        dto.tenantId = shop.getTenantId();
        dto.integrationId = shop.getIntegrationId();
        dto.platform = shop.getPlatform();
        dto.shopCode = shop.getShopCode();
        dto.shopName = shop.getShopName();
        dto.marketplaceId = shop.getMarketplaceId();
        dto.region = shop.getRegion();
        dto.externalShopId = shop.getExternalShopId();
        dto.storeUrl = shop.getStoreUrl();
        dto.apiVersion = shop.getApiVersion();
        dto.apiKeyMasked = mask(shop.getApiKey());
        dto.hasApiSecret = isPresent(shop.getApiSecret());
        dto.hasAccessToken = isPresent(shop.getAccessToken());
        dto.hasRefreshToken = isPresent(shop.getRefreshToken());
        dto.credentialConfigured = shop.hasAnyCredential();
        dto.tokenExpiresAt = shop.getTokenExpiresAt();
        dto.authStatus = shop.getAuthStatus();
        dto.pushBackPolicy = shop.getPushBackPolicy();
        dto.active = shop.isActive();
        dto.lastOrdersSyncedAt = shop.getLastOrdersSyncedAt();
        dto.lastProductsSyncedAt = shop.getLastProductsSyncedAt();
        dto.createdAt = shop.getCreatedAt();
        dto.updatedAt = shop.getUpdatedAt();
        return dto;
    }

    /**
     * 保留首 3 位与末 4 位便于辨认是哪把钥匙，其余以 **** 代替；过短的值整体打码，避免掩码反推出原文。
     */
    private static String mask(String secret) {
        if (!isPresent(secret)) {
            return null;
        }
        String value = secret.trim();
        if (value.length() <= 7) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
