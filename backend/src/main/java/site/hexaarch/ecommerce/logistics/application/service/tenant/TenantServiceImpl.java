package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.tenant.PermissionPoints;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.RoleService;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.TenantService;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户服务实现
 *
 * @author kenyon
 */
@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public TenantServiceImpl(TenantRepository tenantRepository, UserRepository userRepository,
                             RoleService roleService, PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Tenant createTenant(String name, String description, String contactEmail, String contactPhone,
                               String adminUsername, String adminPassword, String adminEmail,
                               String adminFirstName, String adminLastName) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new BusinessException("管理员用户名不能为空");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new BusinessException("管理员密码不能为空");
        }
        // 用户名全局唯一：登录时按用户名查找，重复会导致登录归属混乱
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            throw new BusinessException("用户名已存在: " + adminUsername);
        }

        Tenant tenant = Tenant.create(name, description, contactEmail, contactPhone);
        tenant = tenantRepository.save(tenant);

        // users.email 列 NOT NULL+UNIQUE：管理员邮箱选填，缺省回退联系邮箱，
        // 再缺省用用户名拼占位地址（含用户名，天然唯一），避免留空直接 500
        String effectiveEmail = adminEmail;
        if (effectiveEmail == null || effectiveEmail.isBlank()) {
            effectiveEmail = contactEmail;
        }
        if (effectiveEmail == null || effectiveEmail.isBlank()) {
            effectiveEmail = adminUsername.trim() + "@placeholder.local";
        }

        createTenantAdmin(tenant.getId(), adminUsername, adminPassword, effectiveEmail, adminFirstName, adminLastName);
        return tenant;
    }

    @Override
    public Tenant getTenantById(String id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tenant", id));
    }

    @Override
    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    @Override
    @Transactional
    public Tenant updateTenant(String id, String name, String description, String contactEmail, String contactPhone) {
        Tenant tenant = getTenantById(id);
        tenant.updateInfo(name, description, contactEmail, contactPhone);
        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public Tenant activateTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.activate();
        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public Tenant suspendTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public Tenant deactivateTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.deactivate();
        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public void deleteTenant(String id) {
        Tenant tenant = getTenantById(id);

        List<User> members = userRepository.findByTenantId(id);
        // 守住运营方自己：平台管理员所在的租户不允许删除，删掉就再没有任何账号能恢复现场
        boolean hasPlatformAdmin = members.stream().anyMatch(this::isPlatformAdmin);
        if (hasPlatformAdmin) {
            throw new BusinessException("租户「" + tenant.getName() + "」下仍有平台管理员账号，不允许删除");
        }

        // 业务数据不会被级联（业务表的 tenant_id 没有外键指向 tenants），
        // 有数据时直接拒绝，避免静默留下无主的订单/商品
        long businessRecords = tenantRepository.countBusinessRecords(id);
        if (businessRecords > 0) {
            throw new BusinessException("租户「" + tenant.getName() + "」下仍有 " + businessRecords
                    + " 条业务数据，请先清空或改用「停用租户」");
        }

        // 身份数据随租户一起清理：只删租户行会留下能正常登录的孤儿账号（含其权限点与角色）
        for (User member : members) {
            userRepository.deleteById(member.getId());
        }
        for (Role role : roleService.getRolesByTenantId(id)) {
            roleService.deleteRole(role.getId());
        }
        tenantRepository.deleteById(tenant.getId());
    }

    private boolean isPlatformAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role != null && RoleNames.PLATFORM_ADMIN.equals(role.getName()));
    }

    @Override
    @Transactional
    public User createTenantAdmin(String tenantId, String username, String password, String email, String firstName, String lastName) {
        // 创建租户管理员用户（密码在服务层统一加密）
        User user = User.create(tenantId, username, passwordEncoder.encode(password), email, null, firstName, lastName);

        // 取（或建）该租户的 TENANT_ADMIN 角色，并保证权限点齐全
        Role tenantAdminRole = getOrCreateTenantAdminRole(tenantId);

        // 为用户分配角色
        user.addRole(tenantAdminRole);

        return userRepository.save(user);
    }

    /**
     * 取（或建）指定租户的租户管理员角色，并把权限点重置为租户级全量清单。
     * <p>
     * 幂等：角色按名查找复用，避免每次开户都堆出一个同名角色；
     * 权限点每次对齐，保证「租户管理员登录后能真正用上租户内全部业务功能」。
     * </p>
     */
    private Role getOrCreateTenantAdminRole(String tenantId) {
        Role role = roleService.findByName(tenantId, RoleNames.TENANT_ADMIN)
                .orElseGet(() -> roleService.createRole(
                        tenantId, RoleNames.TENANT_ADMIN, RoleNames.TENANT_ADMIN_DESCRIPTION));
        return roleService.setPermissionsToRole(role.getId(), PermissionPoints.tenantAdminPermissions());
    }
}