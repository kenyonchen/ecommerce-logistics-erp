package site.hexaarch.ecommerce.logistics.domain.tenant;

/**
 * 角色名与系统租户标识的唯一定义处。
 * <p>
 * 全仓角色名只允许在此定义，业务代码一律引用常量，禁止再出现字面量
 * （历史上后端创建的是 {@code ADMIN}、前端守卫期望的是 {@code TENANT_ADMIN}，
 * 同语义两个名字并存，导致用户端登录后被守卫判为无权限）。
 * </p>
 * <p>
 * 这些是编译期常量，因此可以直接用于 {@code @PreAuthorize} 等注解表达式。
 * </p>
 *
 * @author kenyon
 */
public final class RoleNames {

    private RoleNames() {
    }

    /**
     * 平台管理员：系统角色，{@code tenantId = SYSTEM}，持有平台级与租户级全部权限，
     * 并在 {@code TenantIsolationAspect} 中被放行跨租户访问。
     * 仅用于平台管理端（admin-web）的租户与全局用户管理。
     */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /**
     * 租户管理员：租户角色，持有本租户全部业务权限，不含平台级管理权限。
     * 卖家业务端（user-web）的最高权限角色。
     */
    public static final String TENANT_ADMIN = "TENANT_ADMIN";

    /**
     * 历史遗留的租户管理员角色名。已被 {@link #TENANT_ADMIN} 取代，
     * 仅供启动迁移使用，业务代码不得引用。
     */
    public static final String LEGACY_TENANT_ADMIN = "ADMIN";

    /**
     * 系统角色的 tenantId 占位值（系统角色不属于任何租户）。
     */
    public static final String SYSTEM_TENANT_ID = "SYSTEM";

    public static final String PLATFORM_ADMIN_DESCRIPTION = "平台管理员（系统内置，拥有全部权限）";

    public static final String TENANT_ADMIN_DESCRIPTION = "租户管理员（本租户全部业务权限）";

    public static final String LEGACY_TENANT_ADMIN_DESCRIPTION = "Tenant Administrator";
}
