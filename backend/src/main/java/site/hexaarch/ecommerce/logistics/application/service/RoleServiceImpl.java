package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.RoleService;

import java.util.List;
import java.util.stream.Collectors;

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
    public Role createRole(String tenantId, String name, String description) {
        Role role = Role.create(tenantId, name, description);
        return roleRepository.save(role);
    }

    @Override
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
        return roleRepository.findByTenantId("SYSTEM");
    }

    @Override
    public Role updateRole(String id, String name, String description) {
        Role role = getRoleById(id);
        role.updateInfo(name, description);
        return roleRepository.save(role);
    }

    @Override
    public void deleteRole(String id) {
        roleRepository.deleteById(id);
    }

    @Override
    public Role addPermissionToRole(String id, String permission) {
        Role role = getRoleById(id);
        role.addPermission(permission);
        return roleRepository.save(role);
    }

    @Override
    public Role removePermissionFromRole(String id, String permission) {
        Role role = getRoleById(id);
        role.removePermission(permission);
        return roleRepository.save(role);
    }

    @Override
    public Role setPermissionsToRole(String id, List<String> permissions) {
        Role role = getRoleById(id);
        role.setPermissions(permissions.stream().collect(Collectors.toSet()));
        return roleRepository.save(role);
    }
}