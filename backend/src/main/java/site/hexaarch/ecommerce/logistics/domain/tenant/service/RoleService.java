package site.hexaarch.ecommerce.logistics.domain.tenant.service;

import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;

import java.util.List;

/**
 * 角色服务接口
 *
 * @author kenyon
 */
public interface RoleService {
    /**
     * 创建角色
     *
     * @param tenantId    租户ID
     * @param name        角色名称
     * @param description 角色描述
     * @return 创建的角色
     */
    Role createRole(String tenantId, String name, String description);

    /**
     * 创建系统角色
     *
     * @param name        角色名称
     * @param description 角色描述
     * @return 创建的系统角色
     */
    Role createSystemRole(String name, String description);

    /**
     * 根据ID获取角色
     *
     * @param id 角色ID
     * @return 角色
     */
    Role getRoleById(String id);

    /**
     * 根据租户ID获取角色列表
     *
     * @param tenantId 租户ID
     * @return 角色列表
     */
    List<Role> getRolesByTenantId(String tenantId);

    /**
     * 获取所有系统角色
     *
     * @return 系统角色列表
     */
    List<Role> getSystemRoles();

    /**
     * 更新角色信息
     *
     * @param id          角色ID
     * @param name        角色名称
     * @param description 角色描述
     * @return 更新后的角色
     */
    Role updateRole(String id, String name, String description);

    /**
     * 删除角色
     *
     * @param id 角色ID
     */
    void deleteRole(String id);

    /**
     * 为角色添加权限
     *
     * @param id         角色ID
     * @param permission 权限
     * @return 更新后的角色
     */
    Role addPermissionToRole(String id, String permission);

    /**
     * 从角色移除权限
     *
     * @param id         角色ID
     * @param permission 权限
     * @return 更新后的角色
     */
    Role removePermissionFromRole(String id, String permission);

    /**
     * 为角色设置权限集合
     *
     * @param id          角色ID
     * @param permissions 权限集合
     * @return 更新后的角色
     */
    Role setPermissionsToRole(String id, List<String> permissions);
}