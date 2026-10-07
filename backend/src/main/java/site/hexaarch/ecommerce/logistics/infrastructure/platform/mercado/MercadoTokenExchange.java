package site.hexaarch.ecommerce.logistics.infrastructure.platform.mercado;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformOAuthClient;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformToken;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformTokenExchange;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado Libre 令牌交换：用一次性的 {@code refresh_token} 换短期访问令牌。
 * <p>
 * 端点 {@code POST https://api.mercadolibre.com/oauth/token}、{@code grant_type} ∈
 * {@code client_credentials|authorization_code|refresh_token}（docs/spec/platform-api-reference.md §7）。
 * 本适配器只跑 {@code refresh_token} 这一段：集成的凭据模型里没有可暂存一次性 authorization {@code code}
 * 的地方（无该字段、无回调会话），操作员只能把已授权拿到的刷新令牌填进 {@code refreshToken}。
 * 令牌端点 URI 可用 {@code platform.mercado.token-uri} 覆盖，默认值就是核实到的官方字面值。
 * <p>
 * <b>{@code rotatesRefreshToken()} 必须为 {@code true}</b>（§2.2 / §7）：官方口径是「REFRESH_TOKEN 只能使用一次」，
 * 换出新令牌后旧值即作废并返回新值；{@code PlatformCredentials#resolve} 据此把新 {@code refresh_token} 回写落库，
 * 不声明轮换就会在下次刷新时复用已作废的旧令牌而把整条授权链打断。
 * <p>
 * 有效时长<b>只取响应的 {@code expires_in}</b>（§7 的示例是 {@code 10800}，即 6 小时，但那是示例值不是约定值），
 * 由 {@code PlatformOAuthClient} 统一换算成到期时间，本类不写死 TTL——写死了令牌会在真实到期前被判有效而反复 401。
 * <p>
 * <b>客户端凭据放在 JSON 请求体里而不是 {@code Basic} 头</b>：§7 的实测错误信封
 * {@code {"message":"invalid client_id or client_secret","error":"invalid_client","status":400,"cause":[]}}
 * 说明平台是按下文的 body 字段名校验这对值的，因此走 {@code requestByJson} 且不额外发鉴权头
 * （区别于 §4.1 把凭据放 Basic 头的 eBay）。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class MercadoTokenExchange implements PlatformTokenExchange {

    /**
     * 官方核实的令牌端点字面值（§7）
     */
    static final String DEFAULT_TOKEN_URI = MercadoApi.DEFAULT_BASE + "/oauth/token";

    /**
     * §7 的三个 {@code grant_type} 之一
     */
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final PlatformOAuthClient oauth;
    private final String tokenUri;

    public MercadoTokenExchange(PlatformOAuthClient oauth,
                                @Value("${platform.mercado.token-uri:" + DEFAULT_TOKEN_URI + "}") String tokenUri) {
        this.oauth = oauth;
        this.tokenUri = tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri.trim();
    }

    @Override
    public String type() {
        return PlatformAuthType.MERCADO_LIBRE.name();
    }

    @Override
    public boolean rotatesRefreshToken() {
        return true;
    }

    @Override
    public PlatformToken exchange(PlatformCredential credential) {
        String refreshToken = requireField(credential == null ? null : credential.refreshToken(),
                "refreshToken(一次性 refresh_token)");
        String clientId = requireField(credential == null ? null : credential.apiKey(),
                "apiKey(client_id)");
        String clientSecret = requireField(credential == null ? null : credential.apiSecret(),
                "apiSecret(client_secret)");

        // 键名取自 §7 实测错误信息「invalid client_id or client_secret」所校验的那对字段 + OAuth2 的 grant 参数
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        body.put("refresh_token", refreshToken);
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);

        PlatformToken token = oauth.requestByJson(URI.create(tokenUri), body, Map.of(), "Mercado Libre");
        // 只报到期时间与「是否拿到了新的轮换值」，绝不回显任何令牌内容（BR-02）
        log.info("[Mercado] 访问令牌已换取 expiresAt={} refreshRotated={}",
                token.expiresAt(), token.refreshToken() != null);
        return token;
    }

    /**
     * 只报字段名，绝不把凭据值拼进异常消息（BR-02）。
     */
    private static String requireField(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Mercado Libre 令牌交换缺少 " + fieldLabel
                    + "：请在集成的对应字段填写 developers.mercadolibre.com 应用凭据"
                    + "（刷新令牌需授权时带 offline_access 才会返回）", "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return value.trim();
    }
}
