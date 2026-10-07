package site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth;

import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台 OAuth2 令牌端点的共用客户端。
 * <p>
 * 这一层解决的是仓库里一个真实的缺口：{@code refresh_token} 字段一直存在（{@code PlatformIntegration} /
 * {@code platform_integrations.refresh_token}），但**从未被用于任何令牌端点调用**。
 * Shopify 一类「长期访问令牌」平台不需要刷新，所以问题没暴露；Amazon SP-API 只有刷新令牌、
 * 没有长期令牌，不实现刷新就完全无法外呼。
 * <p>
 * 各家的差异只在于「参数放表单还是 JSON」「客户端凭据放 Basic 头还是表单体」「响应是裸对象还是包一层」，
 * 因此这些交给调用方，本类只负责发请求 + 容错解析 + 换算到期时间。
 * 刷新令牌是否轮换由各平台适配器决定，见 {@link PlatformToken#refreshToken()}。
 *
 * @author kenyon
 */
@Component
public class PlatformOAuthClient {

    private final PlatformHttpExchange http;

    public PlatformOAuthClient(PlatformHttpExchange http) {
        this.http = http;
    }

    /**
     * {@code application/x-www-form-urlencoded} 表单式令牌请求（Amazon LWA、eBay OAuth 都是这个形状）。
     */
    public PlatformToken requestByForm(URI tokenUri, Map<String, String> form, Map<String, String> headers, String label) {
        ResponseEntity<String> response = http.postForm(tokenUri, headers, form, label + " 令牌端点");
        return parse(response.getBody(), label);
    }

    /**
     * JSON 请求体式令牌请求（Mercado Libre、Shopee 一类）。
     */
    public PlatformToken requestByJson(URI tokenUri, Map<String, Object> body, Map<String, String> headers, String label) {
        ResponseEntity<String> response = http.postJson(tokenUri, headers, PlatformJson.write(body), label + " 令牌端点");
        return parse(response.getBody(), label);
    }

    /**
     * OAuth2 客户端凭据的 {@code Basic} 头值：{@code base64(client_id:client_secret)}。
     */
    public static String basicAuthHeader(String clientId, String clientSecret) {
        String raw = (clientId == null ? "" : clientId) + ":" + (clientSecret == null ? "" : clientSecret);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 容错解析：字段名与信封形态在各家之间不一致，逐个探测而不是假定一种。
     */
    PlatformToken parse(String jsonBody, String label) {
        Map<String, Object> root = PlatformJson.object(jsonBody);
        Map<String, Object> node = root;
        // 部分平台把令牌包在 data 里（TikTok Shop: {code,message,data:{access_token,...}}）
        Object data = root.get("data");
        if (data instanceof Map<?, ?> && PlatformJson.text((Map<String, Object>) data, "access_token") != null) {
            node = (Map<String, Object>) data;
        }
        String accessToken = firstText(node, "access_token", "accessToken");
        if (accessToken == null) {
            throw new BusinessException(label + " 令牌响应缺少 access_token: " + brief(root),
                    "PLATFORM_TOKEN_RESPONSE_INVALID");
        }
        String refreshToken = firstText(node, "refresh_token", "refreshToken", "refresh");
        return new PlatformToken(accessToken, refreshToken, resolveExpiry(node));
    }

    /**
     * 到期时间三家三种写法：相对秒数（{@code expires_in}）、绝对 epoch（Shopee 的 {@code expire_time}）、
     * ISO 字符串（{@code expires_at}）。epoch 与 ISO 都是绝对时刻，按 UTC 换成本地墙上时间，
     * 与 {@code token_expires_at} 列的既有口径一致。
     */
    private LocalDateTime resolveExpiry(Map<String, Object> node) {
        Long expiresIn = epochOrNull(firstValue(node, "expires_in", "expiresIn"));
        if (expiresIn != null) {
            return LocalDateTime.now().plusSeconds(expiresIn);
        }
        Long expireTime = epochOrNull(firstValue(node, "expire_time", "expireTime"));
        if (expireTime != null) {
            long seconds = expireTime > 99_999_999_999L ? expireTime / 1000L : expireTime;
            return LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
        }
        String expiresAt = firstText(node, "expires_at", "expiresAt");
        if (expiresAt != null) {
            try {
                return PlatformJson.toUtcLocalDateTime(expiresAt);
            } catch (BusinessException e) {
                return null;
            }
        }
        return null;
    }

    private static Long epochOrNull(Object value) {
        return value == null ? null : PlatformJson.nullableLong(value);
    }

    /**
     * 兄弟候选键逐个试。不能写成 {@code PlatformJson.path(node, "expires_in", "expiresIn")}——
     * 那是「先取 {@code expires_in} 再往它的值里下钻」，对标量字段永远返回 {@code null}，
     * 后果是令牌到期时间静默丢失、每次外呼都白白刷新一次。
     */
    private static Object firstValue(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = PlatformJson.text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String brief(Map<String, Object> root) {
        // 只回显非凭据字段：错误响应里 message/error 是排障线索，令牌字段绝不能进日志
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : new String[]{"code", "error", "error_code", "error_description", "message", "msg",
                "sub_code", "sub_msg", "error_msg", "request_id"}) {
            Object value = root.get(key);
            if (value != null) {
                safe.put(key, value);
            }
        }
        return safe.isEmpty() ? "(未给出错误字段)" : safe.toString();
    }
}
