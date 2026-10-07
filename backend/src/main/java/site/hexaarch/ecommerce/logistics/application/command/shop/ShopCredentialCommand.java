package site.hexaarch.ecommerce.logistics.application.command.shop;

import java.time.LocalDateTime;

/**
 * 店铺凭据写入命令，对应 {@code shop:credential_write} 权限点。
 * <p>
 * <b>字段语义：{@code null} 表示保留原值，空串表示清除。</b>外呼侧按凭据是否为空决定是否重新换令牌，
 * 因此「清空」是合法操作，不能被当成「没填」。
 * </p>
 * <p>
 * 提交的凭据不会回显：出参只有掩码与 {@code credentialConfigured} 位（ADR-0013 §4.5）。
 * </p>
 */
public record ShopCredentialCommand(
        String apiKey,
        String apiSecret,
        String accessToken,
        String refreshToken,
        LocalDateTime tokenExpiresAt,
        String marketplaceId,
        String region,
        String externalShopId,
        String storeUrl,
        String apiVersion
) {
}
