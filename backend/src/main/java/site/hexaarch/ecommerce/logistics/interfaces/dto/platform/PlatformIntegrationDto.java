package site.hexaarch.ecommerce.logistics.interfaces.dto.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 平台集成出参 DTO。
 * <p>
 * 替代直接序列化领域聚合 {@link PlatformIntegration}：凭据只以掩码与「是否已配置」标志返回，
 * 任何接口不再回传 {@code apiSecret}/{@code accessToken}（BR-02）。
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "平台集成（凭据以掩码返回）")
public class PlatformIntegrationDto {

    private UUID id;
    private String tenantId;
    private PlatformName platformName;
    @Schema(description = "对接协议，取值见 PlatformAuthType（CUSTOM_SHOP / SHOPIFY / AMAZON_SP_API / EBAY_SELL / ...）")
    private PlatformAuthType authType;
    @Schema(description = "API Key 掩码，永不返回完整值")
    private String apiKeyMasked;
    @Schema(description = "是否已配置 API Secret")
    private boolean hasApiSecret;
    @Schema(description = "是否已配置访问令牌")
    private boolean hasAccessToken;
    @Schema(description = "是否已配置刷新令牌（OAuth 协议需要）")
    private boolean hasRefreshToken;
    private LocalDateTime tokenExpiresAt;
    private String apiVersion;
    private String storeUrl;
    @Schema(description = "平台区域/站点，用于选择 API host")
    private String region;
    @Schema(description = "平台侧店铺/卖家账号标识（非密钥）")
    private String externalShopId;
    @Schema(description = "逗号分隔的市场标识（Amazon marketplaceIds / eBay marketplace_id）")
    private String marketplaceIds;
    private PlatformStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastProductsSyncedAt;
    private LocalDateTime lastOrdersSyncedAt;

    public static PlatformIntegrationDto from(PlatformIntegration integration) {
        if (integration == null) {
            return null;
        }
        PlatformIntegrationDto dto = new PlatformIntegrationDto();
        dto.id = integration.getId();
        dto.tenantId = integration.getTenantId();
        dto.platformName = integration.getPlatformName();
        dto.authType = integration.getAuthType();
        dto.apiKeyMasked = mask(integration.getApiKey());
        dto.hasApiSecret = isPresent(integration.getApiSecret());
        dto.hasAccessToken = isPresent(integration.getAccessToken());
        dto.hasRefreshToken = isPresent(integration.getRefreshToken());
        dto.tokenExpiresAt = integration.getTokenExpiresAt();
        dto.apiVersion = integration.getApiVersion();
        dto.storeUrl = integration.getStoreUrl();
        dto.region = integration.getRegion();
        dto.externalShopId = integration.getExternalShopId();
        dto.marketplaceIds = integration.getMarketplaceIds();
        dto.status = integration.getStatus();
        dto.createdAt = integration.getCreatedAt();
        dto.updatedAt = integration.getUpdatedAt();
        dto.lastProductsSyncedAt = integration.getLastProductsSyncedAt();
        dto.lastOrdersSyncedAt = integration.getLastOrdersSyncedAt();
        return dto;
    }

    /**
     * 保留首 3 位与末 4 位便于运维辨认是哪把钥匙，其余以 **** 代替。
     */
    static String mask(String secret) {
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
