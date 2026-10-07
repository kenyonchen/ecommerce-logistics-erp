package site.hexaarch.ecommerce.logistics.domain.tenant.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link User} 的角色集合行为测试。
 * <p>
 * 重点回归「撤销角色」：{@code Role} 的 equals 由 Lombok 生成、比较全部字段，
 * 而 createdAt 每次构造都不同，因此按对象移除会静默失效（接口返回 200 但角色还在）。
 * 这里锁定「按角色ID判重/移除」这一业务语义。
 * </p>
 *
 * @author kenyon
 */
class UserRoleAssignmentTest {

    /**
     * 模拟持久化层：两次独立加载同一个角色，createdAt 不同但 id 相同。
     */
    private static Role loadRole(String id, String name) {
        Role role = Role.create("tenant-1", name, "desc");
        role.setId(id);
        return role;
    }

    @Test
    @DisplayName("按不同实例加载的同一角色也应能被移除")
    void shouldRemoveRoleLoadedAsDifferentInstance() {
        User user = User.create("tenant-1", "seller", "encoded", "seller@test.com", null, "seller", "test");
        user.addRole(loadRole("role-1", RoleNames.TENANT_ADMIN));

        // 第二次从仓储加载的另一个实例（equals 因 createdAt 不同而为 false）
        user.removeRole(loadRole("role-1", RoleNames.TENANT_ADMIN));

        assertTrue(user.getRoles().isEmpty(), "同一角色的不同实例也应被移除，实际残留: " + user.getRoles().size());
    }

    @Test
    @DisplayName("重复授予同一角色不应产生重复绑定")
    void shouldNotDuplicateRoleOnRepeatedAssign() {
        User user = User.create("tenant-1", "seller", "encoded", "seller@test.com", null, "seller", "test");
        user.addRole(loadRole("role-1", RoleNames.TENANT_ADMIN));
        user.addRole(loadRole("role-1", RoleNames.TENANT_ADMIN));

        assertEquals(1, user.getRoles().size(), "同一角色ID只应保留一条绑定");
    }

    @Test
    @DisplayName("只移除指定角色，其他角色不受影响")
    void shouldKeepOtherRolesWhenRemovingOne() {
        User user = User.create("tenant-1", "seller", "encoded", "seller@test.com", null, "seller", "test");
        user.addRole(loadRole("role-1", RoleNames.TENANT_ADMIN));
        user.addRole(loadRole("role-2", RoleNames.PLATFORM_ADMIN));

        user.removeRole(loadRole("role-2", RoleNames.PLATFORM_ADMIN));

        assertEquals(1, user.getRoles().size());
        assertEquals(RoleNames.TENANT_ADMIN, user.getRoles().iterator().next().getName());
    }

    @Test
    @DisplayName("传入 null 或无 id 的角色不抛异常且不改变集合")
    void shouldIgnoreNullAndIdlessRole() {
        User user = User.create("tenant-1", "seller", "encoded", "seller@test.com", null, "seller", "test");
        user.addRole(loadRole("role-1", RoleNames.TENANT_ADMIN));

        // Role.create 会生成随机 id，这里显式置空以覆盖「无 id 角色」的防御分支
        Role noId = Role.create("tenant-1", "no-id", "desc");
        noId.setId(null);

        user.addRole(null);
        user.removeRole(null);
        user.addRole(noId);

        assertEquals(1, user.getRoles().size());
    }
}
