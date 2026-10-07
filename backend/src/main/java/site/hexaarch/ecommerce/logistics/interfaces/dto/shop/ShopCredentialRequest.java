package site.hexaarch.ecommerce.logistics.interfaces.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.application.command.shop.ShopCredentialCommand;

import java.time.LocalDateTime;

/**
 * 店铺凭据写入入参，对应 {@code shop:credential_write}。
 * <p>
 * <b>不填（{@code null}）= 保留原值；填空串 = 清除该字段。</b>出参不回显提交值，
 * 只回掩码与「是否已配置」位，所以前端无法原样回填，保留语义是必需的。
 * </p>
 * <p>
 * 写入任何一项都会把授权状态打回 PENDING——换 token 等于换数据来源，此前的连通性结论不再可信。
 * </p>
 */
@Getter
@Setter
public class ShopCredentialRequest {

    @Schema(description = "API Key / app id / client_id")
    private String apiKey;
    @Schema(description = "API Secret / app secret / client_secret")
    private String apiSecret;
    @Schema(description = "访问令牌（Shopify 的 shpat_ 令牌等）")
    private String accessToken;
    @Schema(description = "刷新令牌（OAuth 协议）")
    private String refreshToken;
    @Schema(description = "令牌到期时间")
    private LocalDateTime tokenExpiresAt;
    @Schema(description = "站点标识，如 Amazon 的 ATVPDKIKX0DER")
    private String marketplaceId;
    @Schema(description = "区域：Amazon 取 NA/EU/FE")
    private String region;
    @Schema(description = "平台侧店铺/卖家账号标识：Shopee/TikTok 的 shop_id、Mercado 的 user_id")
    private String externalShopId;
    @Schema(description = "店铺地址（Shopify 域名、自建店铺 API 基址）")
    private String storeUrl;
    @Schema(description = "平台接口版本，如 2026-07")
    private String apiVersion;

    public ShopCredentialCommand toCommand() {
        return new ShopCredentialCommand(apiKey, apiSecret, accessToken, refreshToken, tokenExpiresAt,
                marketplaceId, region, externalShopId, storeUrl, apiVersion);
    }
}
