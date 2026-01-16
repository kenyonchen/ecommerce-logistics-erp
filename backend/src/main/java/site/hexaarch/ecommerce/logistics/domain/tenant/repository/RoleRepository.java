package site.hexaarch.ecommerce.logistics.domain.tenant.repository;

import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓库接口
 *
 * @author kenyon
 */
public interface RoleRepository {
    /**
     * 保存角色
     *
     * @param role 角色实体
     * @return 保存后的角色
     */
    Role save(Role role);

    /**
     * 根据ID查找角色
     *
     * @param id 角色ID
     * @return 角色
     */
    Optional<Role> findById(String id);

    /**
     * 根据名称和租户ID查找角色
     *
     * @param name     角色名称
     * @param tenantId 租户ID
     * @return 角色
     */
    Optional<Role> findByNameAndTenantId(String name, String tenantId);

    /**
     * 根据租户ID查找角色列表
     *
     * @param tenantId 租户ID
     * @return 角色列表
     */
    List<Role> findByTenantId(String tenantId);

    /**
     * 删除角色
     *
     * @param id 角色ID
     */
    void deleteById(String id);
}