package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.RoleService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户删除的级联与前置守卫测试。
 * <p>
 * 回归的真实缺陷：{@code users.tenant_id} / {@code roles.tenant_id} 都没有指向 {@code tenants}
 * 的外键，旧实现只删租户行，结果账号与角色原封不动地留着——实测该孤儿账号仍能登录，
 * 并拿到 25 个权限点的令牌，指向一个已经不存在的租户。
 * </p>
 *
 * @author kenyon
 */
class TenantServiceImplDeleteTest {

    private static final String TENANT_ID = "t-100";

    private TenantRepository tenantRepository;
    private UserRepository userRepository;
    private RoleService roleService;
    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        userRepository = mock(UserRepository.class);
        roleService = mock(RoleService.class);
        tenantService = new TenantServiceImpl(tenantRepository, userRepository, roleService,
                mock(PasswordEncoder.class));
    }

    private Tenant activeTenant() {
        Tenant tenant = Tenant.create("冒烟第二租户", "desc", "s@t.com", null);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private User memberOf(String username, String roleName) {
        User user = User.create(TENANT_ID, username, "encoded", username + "@t.com", null, username, "Q");
        user.addRole(role(roleName));
        return user;
    }

    private Role role(String name) {
        Role role = Role.create(TENANT_ID, name, "desc");
        role.setId("role-" + name);
        return role;
    }

    @Test
    @DisplayName("干净租户：账号与角色随租户一起删除，不留孤儿身份")
    void shouldCascadeIdentityData() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
        User admin = memberOf("smokeadm", RoleNames.TENANT_ADMIN);
        when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin));
        when(roleService.getRolesByTenantId(TENANT_ID)).thenReturn(List.of(role(RoleNames.TENANT_ADMIN)));
        when(tenantRepository.countBusinessRecords(TENANT_ID)).thenReturn(0L);

        tenantService.deleteTenant(TENANT_ID);

        verify(userRepository).deleteById(admin.getId());
        verify(roleService).deleteRole("role-" + RoleNames.TENANT_ADMIN);
        verify(tenantRepository).deleteById(TENANT_ID);
    }

    @Test
    @DisplayName("租户下仍有业务数据：拒绝删除，避免留下无主的订单/商品")
    void shouldRefuseWhenBusinessDataRemains() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
        when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        when(tenantRepository.countBusinessRecords(TENANT_ID)).thenReturn(7L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> tenantService.deleteTenant(TENANT_ID));

        assertTrue(error.getMessage().contains("7"), "报错要给出条数，便于运维判断： " + error.getMessage());
        verify(tenantRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("平台管理员所在租户：不允许删除，否则运营方把自己锁死")
    void shouldRefuseToDeletePlatformAdminTenant() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
        when(userRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(memberOf("admin", RoleNames.PLATFORM_ADMIN)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> tenantService.deleteTenant(TENANT_ID));

        assertTrue(error.getMessage().contains("平台管理员"), error.getMessage());
        verify(tenantRepository, never()).deleteById(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("租户不存在：404 语义而不是 500")
    void shouldReportNotFoundForUnknownTenant() {
        when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tenantService.deleteTenant("missing"));
    }
}
