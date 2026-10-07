package site.hexaarch.ecommerce.logistics.infrastructure.platform.amazon;

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
 * Amazon LWA（Login with Amazon）令牌交换：用刷新令牌换 1 小时有效的 SP-API 访问令牌。
 * <p>
 * SP-API <b>只有刷新令牌、没有长期令牌</b>，所以这一步不是优化而是外呼的前置条件
 * （docs/spec/platform-api-reference.md §2.2、§3.2）。端点字面值按官方页取
 * {@code www.amazon.com/ap/oath/v1/token}——历史实现普遍写的 {@code api.amazon.com/auth/o2/token}
 * 已被核实为错，用错 host 就换不到令牌（§0 的三处推翻预设之一）。
 * {@code amazon.ru}/{@code amazon.in} 等区域变体通过 {@code platform.amazon.lwa-token-uri} 覆盖，
 * 默认值就是官方核实到的字面值。
 * <p>
 * LWA 表单要求客户端凭据<b>放在表单体里</b>而不是 Basic 头（§3.2 的四参数形状），
 * 所以走 {@code PlatformOAuthClient#requestByForm} 并且不给额外请求头。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class AmazonLwaTokenExchange implements PlatformTokenExchange {

    /**
     * 官方核实到的令牌端点字面值（§3.2）
     */
    static final String DEFAULT_TOKEN_URI = "https://www.amazon.com/ap/oath/v1/token";

    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final PlatformOAuthClient oauth;
    private final String tokenUri;

    public AmazonLwaTokenExchange(PlatformOAuthClient oauth,
                                  @Value("${platform.amazon.lwa-token-uri:" + DEFAULT_TOKEN_URI + "}") String tokenUri) {
        this.oauth = oauth;
        this.tokenUri = tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri.trim();
    }

    @Override
    public String type() {
        return PlatformAuthType.AMAZON_SP_API.name();
    }

    /**
     * 显式声明不轮换：官方口径是刷新令牌「不会过期」，回写反而会把「一次性授权换出的长期令牌」
     * 覆盖成短生命周期的新值（§2.2 的轮换判定表）。
     */
    @Override
    public boolean rotatesRefreshToken() {
        return false;
    }

    @Override
    public PlatformToken exchange(PlatformCredential credential) {
        String refreshToken = requireField(credential == null ? null : credential.refreshToken(),
                "refreshToken(自授权生成的 refresh_token)");
        String clientId = requireField(credential == null ? null : credential.apiKey(),
                "apiKey(LWA client_id)");
        String clientSecret = requireField(credential == null ? null : credential.apiSecret(),
                "apiSecret(LWA client_secret)");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);

        PlatformToken token = oauth.requestByForm(URI.create(tokenUri), form, Map.of(), "Amazon LWA");
        log.info("[Amazon] LWA 访问令牌已换取 region={} expiresAt={}",
                credential == null ? null : credential.region(), token.expiresAt());
        return token;
    }

    /**
     * 只报字段名，绝不把凭据值拼进异常消息（BR-02）。
     */
    private static String requireField(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Amazon SP-API 令牌交换缺少 " + fieldLabel
                    + "：请在集成的对应字段填写 Seller Central 应用凭据", "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return value.trim();
    }
}
