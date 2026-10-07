package site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 适配器外呼前的凭据解析入口：把聚合转成 {@link PlatformCredential}，并在 OAuth 协议的令牌
 * 临期时先刷新、再回写落库。
 * <p>
 * 这一层补的是仓库里一个静默的缺口：{@code refresh_token} 字段一直存在，但**从未被用于任何令牌端点调用**。
 * Shopify 一类长期令牌平台不需要刷新，所以问题没暴露；Amazon SP-API 只有刷新令牌、没有长期令牌，
 * 不刷新就完全无法外呼。
 * <p>
 * 刷新结果必须落库：不持久化的话每次外呼都要重新换令牌（Amazon 令牌端点限流 1 次/秒，会立刻被打爆），
 * 而且对 Shopee 这种「刷新令牌一次性」的平台，不回写新值等于下一次刷新必然失败。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class PlatformCredentials {

    private final PlatformIntegrationRepository repository;
    private final Map<String, PlatformTokenExchange> exchanges;
    private final Duration refreshSkew;

    public PlatformCredentials(PlatformIntegrationRepository repository,
                               List<PlatformTokenExchange> exchanges,
                               @Value("${platform.oauth.refresh-skew:5m}") Duration refreshSkew) {
        this.repository = repository;
        this.exchanges = index(exchanges);
        // 留出网络往返与本机钟差余量，避免卡在到期边界上反复 401
        this.refreshSkew = refreshSkew == null || refreshSkew.isNegative() ? Duration.ofMinutes(5) : refreshSkew;
    }

    /**
     * 已经注册了令牌交换实现的 OAuth 协议名，用于配置界面提示「这项可以只填刷新令牌」。
     */
    public java.util.Set<String> refreshableProtocols() {
        return exchanges.keySet();
    }

    /**
     * 解析出可直接外呼的凭据；需要且能够刷新时已完成刷新与持久化，传入的聚合也会同步为新令牌。
     */
    public PlatformCredential resolve(PlatformIntegration integration) {
        PlatformCredential credential = PlatformCredential.from(integration);
        if (credential == null || integration == null) {
            return credential;
        }
        PlatformAuthType authType = credential.authType();
        if (authType == null || !authType.oauthTokenBased()) {
            return credential;
        }
        if (!needsRefresh(credential)) {
            return credential;
        }
        PlatformTokenExchange exchange = exchanges.get(authType.name());
        if (exchange == null) {
            // 操作员直接粘贴了长期令牌（或平台没有令牌交换实现），不刷新，交给外呼自己去报错
            return credential;
        }
        PlatformToken token = exchange.exchange(credential);
        integration.applyToken(token.accessToken(), token.expiresAt(),
                exchange.rotatesRefreshToken() ? token.refreshToken() : null);
        try {
            repository.save(integration);
        } catch (Exception e) {
            // 新令牌已换到，落库失败不该作废这次调用；但要喊出来——否则下次又要重新换，
            // 且对一次性刷新令牌的平台来说，没落库的新令牌等于丢了
            log.error("[PlatformCredentials] 协议 {} 刷新出的新令牌未能落库，下次外呼会重新刷新: {}",
                    authType, e.getMessage());
        }
        log.info("[PlatformCredentials] 协议 {} 访问令牌已刷新, expiresAt={}", authType, token.expiresAt());
        return PlatformCredential.from(integration);
    }

    /**
     * 无令牌 → 必须换；有到期时间且临期 → 换；有令牌但平台没给到期时间 → 按长期令牌用，不反复换。
     */
    private boolean needsRefresh(PlatformCredential credential) {
        String accessToken = credential.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return true;
        }
        LocalDateTime expiresAt = credential.tokenExpiresAt();
        return expiresAt != null && !LocalDateTime.now().plus(refreshSkew).isBefore(expiresAt);
    }

    private static Map<String, PlatformTokenExchange> index(List<PlatformTokenExchange> exchanges) {
        Map<String, PlatformTokenExchange> byType = new HashMap<>();
        for (PlatformTokenExchange exchange : exchanges) {
            PlatformTokenExchange previous = byType.put(exchange.type(), exchange);
            if (previous != null) {
                throw new IllegalStateException("平台令牌交换实现注册键冲突: " + exchange.type()
                        + "，已有 " + previous.getClass().getSimpleName()
                        + "，又出现 " + exchange.getClass().getSimpleName());
            }
        }
        return Map.copyOf(byType);
    }
}
