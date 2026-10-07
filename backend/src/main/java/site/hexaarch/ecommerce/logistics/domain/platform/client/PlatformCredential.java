package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.time.LocalDateTime;

/**
 * 一次平台外呼所需的凭据与协议上下文（由 {@code PlatformIntegration} 解析而来，仓储层已解密为明文）。
 * <p>
 * 拉取请求对象里的 {@code storeUrl/apiKey/apiSecret} 是通用自建店铺协议时代的形状，装不下真实平台的
 * 访问令牌与接口版本；本记录把这些「按协议才存在」的字段与通用字段分开，适配器各取所需。
 * 所有字段都可能是敏感值：不要 {@code toString}、不要写日志。
 *
 * @author kenyon
 */
public record PlatformCredential(
        PlatformAuthType authType,
        String tenantId,
        String platform,
        String storeUrl,
        String apiKey,
        String apiSecret,
        String accessToken,
        LocalDateTime tokenExpiresAt,
        String apiVersion,
        String refreshToken,
        String region,
        String externalShopId,
        String marketplaceIds
) {

    public static PlatformCredential from(PlatformIntegration integration) {
        if (integration == null) {
            return null;
        }
        PlatformAuthType authType = integration.getAuthType() == null
                ? PlatformAuthType.CUSTOM_SHOP : integration.getAuthType();
        String platform = integration.getPlatformName() == null ? null : integration.getPlatformName().name();
        return new PlatformCredential(authType, integration.getTenantId(), platform, integration.getStoreUrl(),
                integration.getApiKey(), integration.getApiSecret(), integration.getAccessToken(),
                integration.getTokenExpiresAt(), integration.getApiVersion(), integration.getRefreshToken(),
                integration.getRegion(), integration.getExternalShopId(), integration.getMarketplaceIds());
    }

    /**
     * 逗号分隔的市场标识列表；空白项被丢弃，全空时返回空列表（由适配器决定是报错还是省略参数）。
     */
    public java.util.List<String> marketplaceIdList() {
        if (marketplaceIds == null || marketplaceIds.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(marketplaceIds.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
