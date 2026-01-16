package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.RoleService;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.TenantService;

import java.util.List;

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

    @Autowired
    public TenantServiceImpl(TenantRepository tenantRepository, UserRepository userRepository, RoleService roleService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
    }

    @Override
    public Tenant createTenant(String name, String description, String contactEmail, String contactPhone) {
        Tenant tenant = Tenant.create(name, description, contactEmail, contactPhone);
        return tenantRepository.save(tenant);
    }

    @Override
    public Tenant getTenantById(String id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
    }

    @Override
    public List<Tenant> getAllTenants() {
        // 实现获取所有租户的逻辑
        return List.of();
    }

    @Override
    public Tenant updateTenant(String id, String name, String description, String contactEmail, String contactPhone) {
        Tenant tenant = getTenantById(id);
        tenant.updateInfo(name, description, contactEmail, contactPhone);
        return tenantRepository.save(tenant);
    }

    @Override
    public Tenant activateTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.activate();
        return tenantRepository.save(tenant);
    }

    @Override
    public Tenant suspendTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    @Override
    public Tenant deactivateTenant(String id) {
        Tenant tenant = getTenantById(id);
        tenant.deactivate();
        return tenantRepository.save(tenant);
    }

    @Override
    public void deleteTenant(String id) {
        tenantRepository.deleteById(id);
    }

    @Override
    public User createTenantAdmin(String tenantId, String username, String password, String email, String firstName, String lastName) {
        // 创建租户管理员用户
        User user = User.create(tenantId, username, password, email, null, firstName, lastName);

        // 创建管理员角色
        Role adminRole = roleService.createRole(tenantId, "ADMIN", "Tenant Administrator");

        // 为用户分配角色
        user.addRole(adminRole);

        return userRepository.save(user);
    }
}