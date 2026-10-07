package site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth;

import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;

/**
 * 单个平台的令牌交换实现：用长期凭据（刷新令牌 / 应用密钥）向该平台的令牌端点换取短期访问令牌。
 *
 * @author kenyon
 */
public interface PlatformTokenExchange {

    /**
     * 注册键，取 {@code PlatformAuthType#name()}；与适配器注册表同构，按协议分派。
     */
    String type();

    /**
     * 是否轮换刷新令牌。
     * <p>
     * 这决定要不要把新 {@code refresh_token} 回写落库：填 {@code true} 时若不回写，下次刷新会复用已作废的
     * 旧令牌并把整条授权链打断（Shopee、Mercado Libre、TikTok Shop 属此类，官方口径都是「只可用一次」）；
     * Amazon SP-API 与 eBay 官方明确不轮换，填 {@code false} 即可，回写反而会把「一次性授权码换出的长期令牌」
     * 误覆盖成短生命周期的新值。
     */
    default boolean rotatesRefreshToken() {
        return false;
    }

    /**
     * 向平台令牌端点换取访问令牌。凭据缺失或不合法时抛 {@code BusinessException}，不要返回 null。
     */
    PlatformToken exchange(PlatformCredential credential);
}
