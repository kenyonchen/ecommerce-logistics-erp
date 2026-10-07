package site.hexaarch.ecommerce.logistics.interfaces.dto.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;

import java.time.LocalDateTime;

/**
 * 平台集成创建/更新入参。
 *
 * @author kenyon
 */
@Getter
@Setter
public class PlatformIntegrationSaveRequest {

    private String tenantId;
    private PlatformName platformName;
    @Schema(description = "对接协议，缺省 CUSTOM_SHOP（通用自建店铺HTTP通道）")
    private PlatformAuthType authType;
    private String apiKey;
    private String apiSecret;
    @Schema(description = "访问令牌，Shopify 的 shpat_ 令牌；更新时留空表示保留原令牌")
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
    @Schema(description = "平台接口版本，如 2026-07；留空取全局配置")
    private String apiVersion;
    private String storeUrl;
    @Schema(description = "平台区域/站点，用于选择 API host：Amazon 的 NA/EU/FE、Ozon 的 Seller API 端点选择等")
    private String region;
    @Schema(description = "平台侧店铺/卖家账号标识：Shopee/TikTok 的 shop_id、Mercado Libre 的 user_id")
    private String externalShopId;
    @Schema(description = "逗号分隔的市场标识：Amazon 的 marketplaceIds、eBay 的 marketplace_id")
    private String marketplaceIds;
    private PlatformStatus status;

    /**
     * 转成领域聚合。{@code keepSecretsFrom} 用于更新场景：入参未回传的凭据沿用库里已有值，
     * 因为出参已不再返回明文，前端无法原样回填。
     */
    public PlatformIntegration toAggregate(PlatformIntegration keepSecretsFrom) {
        PlatformIntegration.Builder builder = PlatformIntegration.builder()
                .tenantId(tenantId)
                .platformName(platformName)
                .authType(authType)
                .storeUrl(storeUrl)
                .apiVersion(apiVersion)
                .region(region)
                .externalShopId(externalShopId)
                .marketplaceIds(marketplaceIds)
                .tokenExpiresAt(tokenExpiresAt)
                .status(status);
        builder.apiKey(orKeep(apiKey, keepSecretsFrom == null ? null : keepSecretsFrom.getApiKey()));
        builder.apiSecret(orKeep(apiSecret, keepSecretsFrom == null ? null : keepSecretsFrom.getApiSecret()));
        builder.accessToken(orKeep(accessToken, keepSecretsFrom == null ? null : keepSecretsFrom.getAccessToken()));
        builder.refreshToken(orKeep(refreshToken, keepSecretsFrom == null ? null : keepSecretsFrom.getRefreshToken()));
        return builder.build();
    }
    private static String orKeep(String incoming, String existing) {
        return incoming == null || incoming.isBlank() ? existing : incoming.trim();
    }
}
