package site.hexaarch.ecommerce.logistics.infrastructure.platform.magento;

import lombok.extern.slf4j.Slf4j;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformJson;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Magento / Adobe Commerce 的 Bearer 访问令牌：<b>单次适配器调用</b>内惰性换取并记住结果。
 * <p>
 * 为什么这一家要自己管令牌（docs/spec/platform-api-reference.md §9 + {@code PlatformAuthType#oauthTokenBased}）：
 * {@code MAGENTO} <b>不在</b> OAuth 令牌协议清单里，所以共享的 {@code PlatformCredentials.resolve()}
 * 对它完全不跑——既不会换令牌也不会回写落库。因此适配器只有两条合法来源，按优先级：
 * <ol>
 *   <li><b>操作员粘贴的集成访问令牌</b>（{@code accessToken}）：存在即直接用，<b>一次换令牌调用都不发</b>。
 *       §9 明确 Commerce Cloud/SaaS 实例只能走这条（现版文档已把 admin 取令牌改成 2FA 端点）。</li>
 *   <li><b>管理员用户名口令现换</b>：{@code PUT {base}/rest/V1/integration/admin/token}
 *       体 {@code {"username","password"}}（§9 字面端点，仅 ≤2.3 实例暴露；{@code apiKey}=username、
 *       {@code apiSecret}=password，与 {@code PlatformAuthCatalog} 的 MAGENTO 行同一口径）。</li>
 * </ol>
 * 两个来源都没有 ⇒ 抛 {@code BusinessException} 并点名 {@code accessToken}/{@code apiKey}/{@code apiSecret}。
 * <p>
 * <b>刻意不做跨调用缓存</b>：{@code PlatformCredentials} 不托管本协议，缓存只能落在进程内存里，
 * 而 admin 令牌默认 4 小时有效（§9）——一个长驻内存的令牌会比它的 TTL 活得更久，
 * 到期后表现为莫名其妙的 401，还会让令牌在租户之间串用（本类的持有者是单例 bean 的入参，
 * 一旦做成字段就是全局共享）。代价是每次同步多一发换令牌请求，这是可接受的：它换来的是
 * 「令牌生命周期与调用生命周期一致」。因此本类的实例由 {@link MagentoRestApi#tokenFor} 在每次
 * 适配器调用开头创建，作用域就是那一次调用。
 * <p>
 * <b>2FA 端点未实现，这是有意的</b>：§9 记录现版文档把 admin 取令牌改成了
 * {@code POST /V1/tfa/provider/google/authenticate}（payload 含 {@code otp}），
 * 需要人工提供一次性验证码，与无人值守的同步作业不兼容；SaaS 场景的正规解法就是上面的粘贴令牌。
 * 若某台实例只开放 2FA 路径，上面的 plain PUT 会以 404/401 失败并把原因原样报出，届时按 §9 补路径而不是在此臆造。
 * <p>
 * 本类<b>不</b>重写 {@code toString}：它持有口令与令牌，任何打印都会泄露（BR-01/BR-02）。
 * 令牌与口令也不写日志、不拼进异常消息。
 *
 * @author kenyon
 */
@Slf4j
public final class MagentoAdminToken {

    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private final PlatformHttpExchange http;
    private final URI tokenUri;
    private final String pastedToken;
    private final String username;
    private final String password;

    /**
     * 本次调用内的 memo；为空表示还没换过。不跨实例共享，见类注释。
     */
    private String bearer;

    MagentoAdminToken(PlatformHttpExchange http, URI tokenUri,
                      String pastedToken, String username, String password) {
        this.http = http;
        this.tokenUri = tokenUri;
        this.pastedToken = pastedToken;
        this.username = username;
        this.password = password;
    }

    /**
     * 出网用的令牌字面值。第一次调用时解析，之后复用同一结果。
     */
    public String bearer() {
        if (this.bearer == null) {
            this.bearer = resolve();
        }
        return this.bearer;
    }

    private String resolve() {
        if (pastedToken != null && !pastedToken.isBlank()) {
            return pastedToken.trim();
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("Magento 没有可用的访问令牌来源：请在集成的 accessToken 直接粘贴集成访问令牌"
                    + "（Commerce Cloud/SaaS 只能走这条），或填写 apiKey(管理员用户名) 与 apiSecret(管理员口令)"
                    + " 由适配器调用 integration/admin/token 换取", "PLATFORM_CREDENTIAL_REQUIRED");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put(USERNAME_KEY, username.trim());
        body.put(PASSWORD_KEY, password);
        String raw = http.putJson(tokenUri, Map.of(), PlatformJson.write(body),
                MagentoRestApi.LABEL + " " + MagentoRestApi.ADMIN_TOKEN_PATH).getBody();
        log.info("[Magento] 已通过 integration/admin/token 换取 Bearer 令牌");
        return bareToken(raw);
    }

    /**
     * §9 点名的那条坑：换令牌<b>成功响应是一段裸 JSON 字符串</b>（示例 {@code "asdf3hjklp5iuytre"}），
     * 不是 JSON 对象，所以不能用 {@code PlatformJson.object}（它会拒掉非对象体），这里手工脱引号。
     * <p>
     * 解析不出来时<b>不回显响应体</b>：这是令牌端点，任何一次原样转发都可能把令牌带进日志或前端。
     */
    static String bareToken(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("Magento 换令牌(integration/admin/token)返回空响应，未拿到 Bearer 令牌",
                    "PLATFORM_RESPONSE_INVALID");
        }
        String value = body.trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            throw new BusinessException("Magento 换令牌(integration/admin/token)返回的是对象而不是 §9 约定的裸字符串令牌，"
                    + "通常是实例只开放 2FA 取令牌路径或口令不被接受；该实例请改为在 accessToken 直接填集成访问令牌",
                    "PLATFORM_RESPONSE_INVALID");
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new BusinessException("Magento 换令牌(integration/admin/token)返回空令牌", "PLATFORM_RESPONSE_INVALID");
        }
        return value;
    }
}
