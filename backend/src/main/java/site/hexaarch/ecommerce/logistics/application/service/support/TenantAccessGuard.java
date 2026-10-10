package site.hexaarch.ecommerce.logistics.application.service.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.Set;

/**
 * 读/写路径的租户守卫（静态工具，无依赖注入，便于在服务内直接调用）。
 * <p>
 * {@code TenantIsolationAspect} 只在服务方法存在名为 {@code tenantId} 的入参时才校验，
 * 因此「按 ID 直读直写聚合」（{@code GET/PUT /api/orders/{id}}）与「不传 tenantId 的列表查询」
 * 两条路径完全不受保护 —— 前者能读到别家数据，后者会返回全平台数据（ISS-050）。
 * 本类补上这两条：加载聚合后校验归属，列表查询把未声明的租户收敛到登录租户。
 * </p>
 * <p>
 * 平台管理员（ROLE_PLATFORM_ADMIN）保持豁免，与切面口径一致；无租户上下文（种子数据、
 * 事件线程内的调用）同样跳过，避免把内部调用判成越权。
 * </p>
 *
 * @author kenyon
 */
public final class TenantAccessGuard {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    /**
     * 历史默认桶的字面量。它<b>不是</b>一个客户端可寻址的租户——{@code tenants} 表里没有这一行——
     * 只允许作为"完全没有会话"时的最后兜底值存在（见 {@code TenantWriteFallbackGuardTest}）。
     * 规则本身（"传字面量等同没传"）在 {@code TenantContext.normalizeRequested}，因为
     * {@code TenantIsolationAspect} 跑在本类之前，两处必须共用同一个判定。
     */
    public static final String LEGACY_TENANT_ID = TenantContext.LEGACY_TENANT_ID;

    private TenantAccessGuard() {
    }

    /**
     * 列表查询的租户作用域：租户用户未显式传租户时收敛到登录租户，显式传别家租户则拒绝。
     *
     * @param requestedTenantId 调用方传入的租户ID，可为空
     * @return 实际生效的租户ID
     */
    public static String scopedTenantId(String requestedTenantId) {
        String currentTenantId = currentTenantId();
        String requested = normalizeRequested(requestedTenantId);
        if (currentTenantId == null) {
            return requested;
        }
        if (requested == null) {
            return currentTenantId;
        }
        if (!requested.equals(currentTenantId)) {
            throw new BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED");
        }
        return requested;
    }

    /**
     * 写入路径的租户归属：显式入参优先（租户用户传别家租户仍拒绝），未传时取登录租户。
     * <p>
     * 写入必须有一个明确的归属租户：历史实现只在缺参时回退到 {@code default-tenant}，
     * 而同一次请求的读路径按登录租户收敛（{@code OrderController#resolveTenant}），
     * 两边口径不一致 —— 导入成功的数据落进没人能看见的桶（冒烟 6.2c）。
     * </p>
     *
     * @param requestedTenantId 调用方显式传入的租户ID，可为空
     * @param legacyFallback    完全没有租户上下文时（内部调用、无认证任务）的历史默认桶
     * @return 实际生效的租户ID
     */
    public static String resolveWriteTenant(String requestedTenantId, String legacyFallback) {
        String requested = normalizeRequested(requestedTenantId);
        if (requested != null) {
            String scoped = scopedTenantId(requested);
            return scoped != null ? scoped : requested;
        }
        if (TenantContext.hasTenantId() && !TenantContext.getTenantId().isBlank()) {
            return TenantContext.getTenantId();
        }
        return legacyFallback;
    }

    /**
     * 把"没传"与"传了历史默认桶字面量"归成一类：都是"由会话决定归属"。
     * <p>
     * 实现委托给 {@code TenantContext.normalizeRequested}，与 {@code TenantIsolationAspect} 共用同一份判定：
     * 切面是 {@code @Before}，跑在本类之前，两边口径不一致时客户端带字面量的请求会先被判成越权。
     * 前端的模块级常量 {@code localStorage.getItem('tenantId') || 'default-tenant'} 在登录前就求值，
     * 于是整个会话都带着它发请求——按字面处理要么把租户用户判成越权（实测 400），要么让平台管理员
     * 真的写进没有租户行的幻影桶，两者都不是调用方的意图。
     * </p>
     */
    private static String normalizeRequested(String requestedTenantId) {
        return TenantContext.normalizeRequested(requestedTenantId);
    }

    /**
     * 聚合归属校验：不属于当前租户时按「不存在」处理，避免向越权调用方确认资源存在。
     *
     * @param resourceName     资源名（用于 404 消息）
     * @param resourceTenantId 聚合上的租户ID
     * @param resourceId       聚合ID（用于 404 消息）
     */
    public static void requireOwned(String resourceName, String resourceTenantId, String resourceId) {
        String currentTenantId = currentTenantId();
        if (currentTenantId == null) {
            return;
        }
        if (!currentTenantId.equals(resourceTenantId)) {
            throw new EntityNotFoundException(resourceName, resourceId);
        }
    }

    /**
     * 客户端点名的租户必须真实存在。
     * <p>
     * {@code tenant_id} 是普通列而不是指向 {@code tenants} 的外键（见
     * {@code TenantRepository#countBusinessRecords} 的说明），数据库不会替我们挡住
     * 「把数据归属给一个没有行的租户」。平台管理员的「代客写」只校验角色不校验目标，
     * 于是 ID 打错一位就落进一条谁都查不出来的归属里 —— §13 的幻影巡检只盯
     * {@code default-tenant / NULL / 空串}，抓不到任意错写的 ID（ISS-135，实测 POST
     * {@code /api/platform-integrations} 带 {@code 11111111-…} 返回 200 且行已落库）。
     * </p>
     * <p>
     * 未指定与历史默认桶字面量不参与判定：{@code default-tenant} 在 {@code tenants} 里本来就没有行，
     * 把它判红会把 ISS-118 定下的兜底口径重新变成报错；兜底桶归 §13 巡检管，不归请求校验管。
     * </p>
     *
     * @param requestedTenantId 调用方显式传入的租户ID，可为空或历史默认桶字面量
     * @param tenantRepository  租户仓库
     */
    public static void requireExistingTenant(String requestedTenantId, TenantRepository tenantRepository) {
        String tenantId = normalizeRequested(requestedTenantId);
        if (tenantId == null) {
            return;
        }
        if (tenantRepository.findById(tenantId).isEmpty()) {
            throw new BusinessException("租户不存在，无法把数据归属给它: " + tenantId, "TENANT_NOT_FOUND");
        }
    }

    /**
     * 当前登录租户；平台管理员或无租户上下文时返回 {@code null}，表示「不按租户过滤」。
     *
     * @return 生效的租户ID，或 null
     */
    public static String currentTenantId() {
        if (isPlatformAdmin() || !TenantContext.hasTenantId()) {
            return null;
        }
        return TenantContext.getTenantId();
    }

    private static boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return authorities.contains(ROLE_PLATFORM_ADMIN);
    }
}
