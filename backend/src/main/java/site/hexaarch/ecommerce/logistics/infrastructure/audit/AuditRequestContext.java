package site.hexaarch.ecommerce.logistics.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

/**
 * 审计留痕所需的请求侧上下文（操作人、租户、来源 IP、traceId）取值器。
 * <p>
 * 抽取出来的原因：{@code AuditAspect} 与登录链路（认证前拿不到 SecurityContext）
 * 都要同一套取值口径，两处各写一遍会很快漂移成"看板上的 IP 与日志里的不是一回事"。
 * </p>
 *
 * @author kenyon
 */
@Component
public class AuditRequestContext {

    private static final String TRACE_ID_KEY = "traceId";
    private static final int MAX_TEXT_LENGTH = 500;
    private static final String ANONYMOUS = "anonymousUser";

    private final UserRepository userRepository;

    public AuditRequestContext(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 当前登录名；匿名或未认证时为 null
     */
    public String actorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || ANONYMOUS.equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * 令牌里只有登录名，操作人 ID 需要回库取一次；
     * 审计端点是低频敏感操作，多一次查询换可追溯的主体 ID。
     */
    public String actorId(String actorName) {
        if (actorName == null || actorName.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(actorName).map(User::getId).orElse(null);
    }

    public String tenantId() {
        return TenantContext.hasTenantId() && !TenantContext.getTenantId().isBlank()
                ? TenantContext.getTenantId() : null;
    }

    /**
     * 与 ApiLoggingFilter 同口径：代理在前时取 X-Forwarded-For 首段
     */
    public String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim());
        }
        return truncate(request.getRemoteAddr());
    }

    public String traceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }
}
