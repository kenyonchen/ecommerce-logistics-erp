package site.hexaarch.ecommerce.logistics.infrastructure.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.Set;

/**
 * 租户隔离AOP切面。
 * <p>
 * 在应用服务层方法执行前做租户访问校验：
 * 1. 当前请求携带租户上下文（JWT 中的 tenantId，由 JwtAuthenticationFilter 写入）时，
 *    校验方法入参中的 tenantId 是否与上下文一致；
 * 2. 平台管理员（ROLE_PLATFORM_ADMIN）可跨租户操作，不做限制；
 * 3. 不一致则抛出业务异常，防止横向越权（跨租户数据访问）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Aspect
@Component
public class TenantIsolationAspect {

    private static final String PARAMETER_TENANT_ID = "tenantId";
    /** 与 Spring Security 的 ROLE_ 前缀约定一致，角色名本身由 {@link RoleNames} 统一定义 */
    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    /**
     * 定义切点：所有Repository方法
     */
    @Pointcut("execution(* site.hexaarch.ecommerce.logistics.domain..*Repository.*(..))")
    public void repositoryMethods() {
    }

    /**
     * 定义切点：所有应用服务方法
     */
    @Pointcut("execution(* site.hexaarch.ecommerce.logistics.application.service..*(..))")
    public void serviceMethods() {
    }

    /**
     * 在服务方法执行前进行租户越权校验
     */
    @Before("serviceMethods()")
    public void beforeServiceMethod(JoinPoint joinPoint) {
        if (!TenantContext.hasTenantId()) {
            // 匿名接口（登录/刷新等）或未携带租户信息的调用，交由认证与业务层处理
            return;
        }

        String currentTenantId = TenantContext.getTenantId();
        if (isPlatformAdmin()) {
            return;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (PARAMETER_TENANT_ID.equals(parameterNames[i]) && args[i] instanceof String requestedTenantId
                    && requestedTenantId != null && !requestedTenantId.isBlank()
                    && !requestedTenantId.equals(currentTenantId)) {
                log.warn("[TenantIsolation] 拒绝跨租户访问: user={} currentTenant={} requestedTenant={} method={}",
                        SecurityContextHolder.getContext().getAuthentication() == null
                                ? "anonymous"
                                : SecurityContextHolder.getContext().getAuthentication().getName(),
                        currentTenantId, requestedTenantId, signature.toShortString());
                throw new BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED");
            }
        }
    }

    /**
     * Repository层仅做调试追踪，不做过滤（数据隔离由服务层参数校验保障）
     */
    @Before("repositoryMethods()")
    public void beforeRepositoryMethod(JoinPoint joinPoint) {
        if (log.isTraceEnabled() && TenantContext.hasTenantId()) {
            log.trace("[TenantIsolation] tenant={} repositoryMethod={}",
                    TenantContext.getTenantId(), joinPoint.getSignature().toShortString());
        }
    }

    private boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return authorities.contains(ROLE_PLATFORM_ADMIN);
    }
}
