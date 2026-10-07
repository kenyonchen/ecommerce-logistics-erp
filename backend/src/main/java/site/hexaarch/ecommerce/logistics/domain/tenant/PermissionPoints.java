package site.hexaarch.ecommerce.logistics.domain.tenant;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限点目录的唯一定义处。
 * <p>
 * 权限点按作用域分两段：租户级业务权限（{@code 模块:动作}）与平台级管理权限。
 * 角色与权限的对应关系是「有意的业务决策」，因此把两组清单都收敛到这里，
 * 由 {@link #tenantAdminPermissions()} / {@link #platformAdminPermissions()} 暴露给种子逻辑使用。
 * </p>
 * <p>
 * 注意：前端 user-web 的角色管理页另有一份 {@code PERMISSION_PRESETS} 硬编码清单，
 * 修改本文件时需同步该清单（缺失项会导致「后端有权限、界面配不出来」）。
 * </p>
 *
 * @author kenyon
 */
public final class PermissionPoints {

    private PermissionPoints() {
    }

    /**
     * 租户级业务权限：订单、商品、客户、店铺、仓储、财务、采购、物流的读写与导入，
     * 以及本租户内的子账号（用户）与角色管理。
     * 租户管理员持有全部 25 项。
     * <p>
     * {@code shop:*} 四项按「读／主数据写／凭据写／外呼」拆开而非合并：把已有店铺指向别人的
     * token 就能用受害者配额拉回攻击者的数据并污染订单归属，所以凭据写入必须是独立一点，
     * 允许老板把 {@code shop:read + shop:sync} 给运营而不交出换 token 的能力。
     * </p>
     * <p>
     * {@code user:manage} / {@code role:manage} 允许租户管理员创建子账号、按需授权，
     * 控制器层强制收敛在本租户内：按 id 操作会校验归属，且碰不到平台管理员账号与系统角色。
     * 平台管理员的跨租户用户管理能力由 {@code ROLE_PLATFORM_ADMIN} 角色承载，不依赖权限点。
     * </p>
     */
    public static final List<String> TENANT_SCOPED_PERMISSIONS = List.of(
            "order:read", "order:write", "order:import",
            "product:read", "product:write", "product:import",
            "customer:read", "customer:write", "customer:import",
            "shop:read", "shop:write", "shop:credential_write", "shop:sync",
            "warehouse:read", "warehouse:write",
            "finance:read", "finance:write", "finance:import",
            "purchase:read", "purchase:write",
            "logistics:read", "logistics:write", "logistics:import",
            "user:manage", "role:manage"
    );

    /**
     * 平台级管理权限：平台集成/店铺对接等全局配置管理。
     * 仅平台管理员持有——租户隔离切面会让平台管理员跨租户放行，
     * 这项权限不应下沉给租户管理员。
     */
    public static final List<String> PLATFORM_SCOPED_PERMISSIONS = List.of(
            "platform:manage"
    );

    /**
     * 全部权限点（26 项 = 25 租户级 + 1 平台级），供平台管理员角色使用。
     */
    public static final List<String> ALL_PERMISSIONS;

    static {
        List<String> all = new ArrayList<>(TENANT_SCOPED_PERMISSIONS);
        all.addAll(PLATFORM_SCOPED_PERMISSIONS);
        ALL_PERMISSIONS = List.copyOf(all);
    }

    /**
     * 租户管理员应持有的权限点
     */
    public static List<String> tenantAdminPermissions() {
        return TENANT_SCOPED_PERMISSIONS;
    }

    /**
     * 平台管理员应持有的权限点
     */
    public static List<String> platformAdminPermissions() {
        return ALL_PERMISSIONS;
    }
}
