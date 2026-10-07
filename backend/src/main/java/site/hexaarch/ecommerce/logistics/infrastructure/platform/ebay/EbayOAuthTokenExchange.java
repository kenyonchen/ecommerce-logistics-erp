package site.hexaarch.ecommerce.logistics.infrastructure.platform.ebay;

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
 * eBay OAuth2 令牌交换：用刷新令牌换 2 小时有效的 Sell API 访问令牌。
 * <p>
 * eBay Sell APIs 走标准 OAuth2 用户授权，外呼只认 {@code Authorization: Bearer <token>}，令牌过期即 401，
 * 因此这一步是所有 eBay 外呼的前置（docs/spec/platform-api-reference.md §4.1、§2.2）。
 * 端点字面值按官方 OpenAPI spec 取 {@code https://api.ebay.com/identity/v1/oauth2/token}；
 * 沙箱与生产同为 {@code apiz} 前缀（沙箱 {@code https://apiz.sandbox.ebay.com}），令牌端点是否随之迁移
 * spec 未明文，故本类把令牌 URI 做成可覆盖项 {@code platform.ebay.token-uri}，默认值就是核实到的生产字面值。
 * <p>
 * 与 Amazon LWA 的关键差异：eBay 把客户端凭据放在 <b>{@code Basic} 头</b>而非表单体（§4.1
 * {@code Authorization: Basic base64(client_id:client_secret)}），表单只带 {@code grant_type}、
 * {@code refresh_token} 与 {@code scope}。scope 取两个 fulfillment/inventory 授权字面值，空格分隔（§4.1）。
 *
 * @author kenyon
 */
@Component
public class EbayOAuthTokenExchange implements PlatformTokenExchange {

    /**
     * 官方核实到的令牌端点字面值（§4.1）
     */
    static final String DEFAULT_TOKEN_URI = "https://api.ebay.com/identity/v1/oauth2/token";

    /**
     * §4.1 请求体里的 scope 字面值：fulfillment 与 inventory 两把，空格分隔。
     * 当前适配器只接 fulfillment 域，但 catalog 与 §4.1 都按这两把发放，保留 inventory 以免换令牌时被拒。
     */
    static final String SCOPE = "https://api.ebay.com/oauth/api_scope/sell.fulfillment"
            + " https://api.ebay.com/oauth/api_scope/sell.inventory";

    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final PlatformOAuthClient oauth;
    private final String tokenUri;

    public EbayOAuthTokenExchange(PlatformOAuthClient oauth,
                                  @Value("${platform.ebay.token-uri:" + DEFAULT_TOKEN_URI + "}") String tokenUri) {
        this.oauth = oauth;
        this.tokenUri = tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri.trim();
    }

    @Override
    public String type() {
        return PlatformAuthType.EBAY_SELL.name();
    }

    /**
     * 显式声明不轮换：§2.2 判定表列 eBay「文档未述及轮换，实测以响应为准」，
     * 刷新令牌约 18 个月有效（§4.1）；把它当成一次性令牌回写反而会覆盖掉长期凭据。
     */
    @Override
    public boolean rotatesRefreshToken() {
        return false;
    }

    @Override
    public PlatformToken exchange(PlatformCredential credential) {
        String refreshToken = requireField(credential == null ? null : credential.refreshToken(),
                "refreshToken(用户授权返回的 refresh_token)");
        String clientId = requireField(credential == null ? null : credential.apiKey(),
                "apiKey(OAuth appid)");
        String clientSecret = requireField(credential == null ? null : credential.apiSecret(),
                "apiSecret(OAuth secret)");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", PlatformOAuthClient.basicAuthHeader(clientId, clientSecret));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        form.put("refresh_token", refreshToken);
        form.put("scope", SCOPE);

        PlatformToken token = oauth.requestByForm(URI.create(tokenUri), form, headers, "eBay OAuth");
        return token;
    }

    /**
     * 只报字段名，绝不把凭据值拼进异常消息（BR-02）。
     */
    private static String requireField(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("eBay Sell 令牌交换缺少 " + fieldLabel
                    + "：请在集成的对应字段填写 developer.ebay.com 应用凭据与用户授权的 refresh_token",
                    "PLATFORM_CREDENTIAL_REQUIRED");
        }
        return value.trim();
    }
}
