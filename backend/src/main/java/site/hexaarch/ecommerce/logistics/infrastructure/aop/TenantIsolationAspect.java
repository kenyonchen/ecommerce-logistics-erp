package site.hexaarch.ecommerce.logistics.infrastructure.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.Set;

/**
 * 租户隔离AOP切面。
 * <p>
 * 在应用服务层方法执行前做租户访问校验：
 * 1. 当前请求携带租户上下文（JWT 中的 tenantId，由 JwtAuthenticationFilter 写入）时，
 *    校验方法入参中的 tenantId 是否与上下文一致；
 * 2. 平台管理员（ROLE_PLATFORM_ADMIN）可跨租户操作，但<b>写</b>请求点名的租户必须真实存在 ——
 *    业务表的 {@code tenant_id} 不是外键，凭空点名会把数据落进 {@code tenants} 表里查无此行的
 *    幻影桶（ISS-135）；读请求不校验（读一个不存在的租户只会返回空页，判 400 会改掉所有带
 *    {@code tenantId} 的 GET 契约，见 {@link #isReadRequest()}）；
 * 3. 不一致则抛出业务异常，防止横向越权（跨租户数据访问）；
 * 4. 入参是历史默认桶字面量（{@code default-tenant}）时按「未指定租户」处理，不参与越权判定 ——
 *    本切面跑在服务体之前，归一化不放在这里就轮不到 {@code TenantAccessGuard.resolveWriteTenant}。
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

    private final TenantRepository tenantRepository;

    @Autowired
    public TenantIsolationAspect(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

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
        boolean platformAdmin = isPlatformAdmin();
        // 动词在一次请求里是常量，循环外算一次。只用于存在性校验：越权判定对读写同样成立，不能放宽。
        boolean writeIntent = !isReadRequest();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (!PARAMETER_TENANT_ID.equals(parameterNames[i]) || !(args[i] instanceof String rawTenantId)) {
                continue;
            }
            // 归一化必须先于越权判定：历史默认桶字面量表示"由会话决定归属"，不是跨租户意图。
            // 本切面是 @Before，跑在服务体里 resolveWriteTenant 之前，不在这儿归一化就会先把请求判成 400。
            String requestedTenantId = TenantContext.normalizeRequested(rawTenantId);
            if (requestedTenantId == null) {
                if (rawTenantId != null && !rawTenantId.isBlank()) {
                    log.info("[TenantIsolation] 入参是历史默认桶字面量，按未指定租户处理: requestedTenant={} method={}",
                            rawTenantId, signature.toShortString());
                }
                continue;
            }
            if (requestedTenantId.equals(currentTenantId)) {
                continue;
            }
            if (platformAdmin) {
                // 平台管理员可跨租户，但"跨"的目标必须是真实存在的租户：业务表的 tenant_id 是普通列
                // 而不是指向 tenants 的外键，凭空点名一个不存在的 UUID 会让数据落进谁也查不出来的幻影桶
                // （ISS-135 实测：POST /api/platform-integrations 带随机 UUID 返回 200 并行落库）。
                // 本租户入参在上面已经 continue，所以只有平台管理员的跨租户写才会查这一次库。
                // 写侧口径由当前请求的动词决定，见 isReadRequest()。
                if (writeIntent) {
                    TenantAccessGuard.requireExistingTenant(requestedTenantId, tenantRepository);
                }
                continue;
            }
            log.warn("[TenantIsolation] 拒绝跨租户访问: user={} currentTenant={} requestedTenant={} method={}",
                    SecurityContextHolder.getContext().getAuthentication() == null
                            ? "anonymous"
                            : SecurityContextHolder.getContext().getAuthentication().getName(),
                    currentTenantId, requestedTenantId, signature.toShortString());
            throw new BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED");
        }
    }

    /**
     * 当前线程是否在处理一次读请求（GET / HEAD / OPTIONS）。
     * <p>
     * ISS-135 的收口面只管写：给不存在的租户归属数据会造出 {@code tenants} 表里查无此行的幻影桶，
     * 而按不存在的租户查询只会返回空页。本切面跑在服务方法之前，签名里读不出读写意图
     * （{@code getXxx}/{@code createXxx} 的命名约定并不可靠），只能取当前请求的动词。
     * </p>
     * <p>
     * 没有请求上下文时（定时任务、事件线程、单测直接调用）按<b>写</b>处理：宁可多问一次存在性，
     * 也不让后台任务安静地把数据写进无人可见的桶里。
     * </p>
     */
    private boolean isReadRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return false;
        }
        String method = servletAttributes.getRequest().getMethod();
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method);
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
