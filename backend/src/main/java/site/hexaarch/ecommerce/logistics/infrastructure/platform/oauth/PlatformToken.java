package site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth;

import java.time.LocalDateTime;

/**
 * 一次 OAuth 令牌端点调用的结果。
 * <p>
 * {@code refreshToken} 只在平台<b>确实轮换</b>时非空（eBay、Amazon SP-API 明确不轮换，返回 null；
 * Shopee 的刷新令牌一次性，必须回写）。所有字段都可能是敏感值：不要 {@code toString}、不要写日志。
 *
 * @author kenyon
 */
public record PlatformToken(String accessToken, String refreshToken, LocalDateTime expiresAt) {

    public boolean expiresWithin(java.time.Duration window) {
        return expiresAt != null && !LocalDateTime.now().plus(window).isBefore(expiresAt);
    }

    /**
     * 覆写 record 默认实现：默认 {@code toString} 会把令牌原样打出来，
     * 一旦进日志或异常消息就违反 BR-02（凭据不出现在任何出参与日志）。
     */
    @Override
    public String toString() {
        return "PlatformToken{accessToken=" + (accessToken == null ? "null" : "***)")
                + ", refreshToken=" + (refreshToken == null ? "null" : "***")
                + ", expiresAt=" + expiresAt + "}";
    }
}
