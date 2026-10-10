package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 令牌签发的账号/租户状态守卫测试（登录与 /auth/refresh 共用同一条规则）。
 * <p>
 * 回归的真实缺陷：租户被停用甚至被删除后，其账号在库里完好无损，
 * 而认证链路过去只看 {@code users.status}——实测孤儿账号仍可登录并拿到全量权限点，
 * {@code /auth/refresh} 更是绕过认证直接重签令牌。
 * </p>
 *
 * @author kenyon
 */
class AccountLoginGuardTest {

    private static final String TENANT_ID = "t-200";

    private TenantRepository tenantRepository;
    private AccountLoginGuard guard;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        guard = new AccountLoginGuard(tenantRepository);
    }

    private Tenant tenant(TenantStatusSetter configure) {
        Tenant tenant = Tenant.create("租户A", "desc", "a@t.com", null);
        tenant.setId(TENANT_ID);
        configure.apply(tenant);
        return tenant;
    }

    /** Tenant 只有 activate/suspend/deactivate 三个状态迁移方法，用它表达目标状态 */
    private interface TenantStatusSetter {
        void apply(Tenant tenant);
    }

    private User user(String username, String roleName) {
        User user = User.create(TENANT_ID, username, "encoded", username + "@t.com", null, username, "Q");
        Role role = Role.create(TENANT_ID, roleName, "desc");
        role.setId("role-" + roleName);
        user.addRole(role);
        return user;
    }

    @Test
    @DisplayName("租户 ACTIVE：正常放行")
    void shouldAllowActiveTenant() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant(t -> t.activate())));

        assertTrue(guard.isUsable(user("seller", RoleNames.TENANT_ADMIN)));
    }

    @Test
    @DisplayName("租户被暂停：其账号不得再拿到令牌，并说明被挡的原因")
    void shouldBlockSuspendedTenant() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant(Tenant::suspend)));

        User seller = user("seller", RoleNames.TENANT_ADMIN);
        assertFalse(guard.isUsable(seller));
        assertTrue(guard.describeBlocker(seller).contains("SUSPENDED"), guard.describeBlocker(seller));
    }

    @Test
    @DisplayName("租户已被删除（查不到）：孤儿账号同样拒绝——这正是过去能登录的那条路径")
    void shouldBlockAccountOfDeletedTenant() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        User orphan = user("smokeadm", RoleNames.TENANT_ADMIN);
        assertFalse(guard.isUsable(orphan));
        assertTrue(guard.describeBlocker(orphan).contains("已不存在"), guard.describeBlocker(orphan));
    }

    @Test
    @DisplayName("平台管理员例外：停用其所属租户不能把运营方自己锁在门外")
    void shouldKeepPlatformAdminUsable() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant(Tenant::suspend)));

        assertTrue(guard.isUsable(user("admin", RoleNames.PLATFORM_ADMIN)));
    }

    @Test
    @DisplayName("账号自身被停用或锁定：拒绝，且原因文案落在账号上而不是租户上")
    void shouldBlockInactiveOrLockedAccount() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant(t -> t.activate())));

        User inactive = user("seller", RoleNames.TENANT_ADMIN);
        inactive.setStatus(UserStatus.INACTIVE);
        assertFalse(guard.isUsable(inactive));
        assertTrue(guard.describeBlocker(inactive).contains("INACTIVE"), guard.describeBlocker(inactive));

        User locked = user("locked-seller", RoleNames.TENANT_ADMIN);
        locked.setStatus(UserStatus.LOCKED);
        assertFalse(guard.isUsable(locked));
    }

    @Test
    @DisplayName("没有租户归属的账号：不放行（无法界定它能看哪个租户的数据）")
    void shouldBlockUserWithoutTenant() {
        User stray = User.create(null, "stray", "encoded", "stray@t.com", null, "stray", "Q");
        stray.addRole(roleWithoutTenant());

        assertFalse(guard.isUsable(stray));
    }

    private Role roleWithoutTenant() {
        Role role = Role.create(null, RoleNames.TENANT_ADMIN, "desc");
        role.setId("role-stray");
        return role;
    }
}
