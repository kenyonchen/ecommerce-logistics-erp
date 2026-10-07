package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformOAuthClient;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformToken;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformTokenExchange;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shopee 访问令牌交换：用一次性的 {@code refresh_token} 换 4 小时有效的 {@code access_token}。
 * <p>
 * 端点 {@code POST /api/v2/auth/access_token/get}（§6）。签名按 §6 的 <b>Public 级</b>口径
 * {@code partner_id + apiPath + timestamp}——换取时还没有 {@code access_token} 可拼，所以走
 * {@link ShopeeSignature#baseString(String, String, long)} 而不是 Shop 级那条。
 * <p>
 * <b>{@code rotatesRefreshToken()} 必须为 {@code true}</b>（§2.2 / §6）：Shopee 的刷新令牌「只可用一次」，
 * 换出新令牌后旧值即作废，{@code PlatformCredentials#resolve} 会把新 {@code refresh_token} 回写落库；
 * 若不声明轮换，下次刷新会复用已作废的旧令牌而把整条授权链打断。
 * <p>
 * <b>ISS-065 未核实点</b>：§6 核实到了刷新端点<b>名字</b>与「{@code access_token} 4h、{@code refresh_token} 一次性」，
 * 但没核实请求体的<b>确切键名</b>。这里按「最小可辩护」放 {@code partner_id/timestamp/sign/refresh_token/shop_id}，
 * 待填钥匙实测校正（§6 的「未核实」口径）。
 * <p>
 * <b>不实现 {@code code} 换令牌的首次授权（{@code POST /api/v2/auth/token/get}）</b>：本仓库的集成凭据模型里没有
 * 可暂存一次性 authorization {@code code} 的地方（无该字段、无回调会话），操作员只能把已换好的
 * {@code access_token}/{@code refresh_token} 直接填入，故适配器只做「刷新已有 refresh_token」这一段。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopeeTokenExchange implements PlatformTokenExchange {

    private final PlatformOAuthClient oauth;
    private final ShopeeOpenApi api;

    public ShopeeTokenExchange(PlatformOAuthClient oauth, ShopeeOpenApi api) {
        this.oauth = oauth;
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPEE.name();
    }

    @Override
    public boolean rotatesRefreshToken() {
        return true;
    }

    @Override
    public PlatformToken exchange(PlatformCredential credential) {
        String refreshToken = requireRefreshToken(credential);
        String partnerId = api.requirePartnerId(credential);
        String partnerKey = api.requirePartnerKey(credential);
        String shopId = api.requireShopId(credential);

        long timestamp = PlatformJson.toEpochSeconds(LocalDateTime.now(ZoneOffset.UTC));
        String sign = ShopeeSignature.sign(partnerKey,
                ShopeeSignature.baseString(partnerId, ShopeeOpenApi.AUTH_ACCESS_TOKEN_PATH, timestamp));

        // §6 未核实刷新请求体键名（ISS-065）：公共参数 + refresh_token + shop_id，签名走 Public 级 base string
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partner_id", partnerId);
        body.put("timestamp", timestamp);
        body.put("sign", sign);
        body.put("refresh_token", refreshToken);
        body.put("shop_id", shopId);

        URI tokenUri = api.publicLevelUri(credential, ShopeeOpenApi.AUTH_ACCESS_TOKEN_PATH, timestamp, sign);
        PlatformToken token = oauth.requestByJson(tokenUri, body, api.envHeaders(credential), "Shopee");
        // 只报到期时间，不回显任何令牌/刷新令牌/签名值（BR-02）
        log.info("[Shopee] 访问令牌已换取 expiresAt={}", token.expiresAt());
        return token;
    }

    private static String requireRefreshToken(PlatformCredential credential) {
        String refreshToken = credential == null ? null : credential.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Shopee 令牌交换缺少 refreshToken(一次性刷新令牌)："
                    + "§6 记 refresh_token 30 天且仅可用一次，请在集成的 refreshToken 字段填写卖家授权拿到的刷新令牌",
                    "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return refreshToken.trim();
    }
}
