package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.RoleService;

import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色服务实现
 *
 * @author kenyon
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Autowired
    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public Role createRole(String tenantId, String name, String description) {
        Role role = Role.create(tenantId, name, description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role createSystemRole(String name, String description) {
        Role role = Role.createSystemRole(name, description);
        return roleRepository.save(role);
    }

    @Override
    public Role getRoleById(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Override
    public List<Role> getRolesByTenantId(String tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Role> getSystemRoles() {
        return roleRepository.findByTenantId(RoleNames.SYSTEM_TENANT_ID);
    }

    /**
     * 按名称查找租户内的角色（幂等创建角色时复用，避免同名角色重复堆积）
     *
     * @param tenantId 租户ID
     * @param name     角色名
     * @return 角色，不存在时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<Role> findByName(String tenantId, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return roleRepository.findByNameAndTenantId(name, tenantId);
    }

    @Override
    @Transactional
    public Role updateRole(String id, String name, String description) {
        Role role = getRoleById(id);
        role.updateInfo(name, description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void deleteRole(String id) {
        roleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Role addPermissionToRole(String id, String permission) {
        Role role = getRoleById(id);
        role.addPermission(permission);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role removePermissionFromRole(String id, String permission) {
        Role role = getRoleById(id);
        role.removePermission(permission);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role setPermissionsToRole(String id, List<String> permissions) {
        Role role = getRoleById(id);
        // 通过领域方法逐条校验权限点格式，避免绕过规则直接写集合
        role.resetPermissions(permissions == null ? List.of() : permissions);
        return roleRepository.save(role);
    }
}