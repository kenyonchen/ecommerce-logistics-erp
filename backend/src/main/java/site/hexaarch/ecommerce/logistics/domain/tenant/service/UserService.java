package site.hexaarch.ecommerce.logistics.domain.tenant.service;

import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author kenyon
 */
public interface UserService {
    /**
     * 创建用户
     *
     * @param tenantId  租户ID
     * @param username  用户名
     * @param password  密码
     * @param email     邮箱
     * @param phone     电话
     * @param firstName 名
     * @param lastName  姓
     * @return 创建的用户
     */
    User createUser(String tenantId, String username, String password, String email, String phone, String firstName, String lastName);

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 用户
     */
    User getUserById(String id);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户
     */
    User getUserByUsername(String username);

    /**
     * 根据租户ID获取用户列表
     *
     * @param tenantId 租户ID
     * @return 用户列表
     */
    List<User> getUsersByTenantId(String tenantId);

    /**
     * 更新用户信息
     *
     * @param id        用户ID
     * @param email     邮箱
     * @param phone     电话
     * @param firstName 名
     * @param lastName  姓
     * @return 更新后的用户
     */
    User updateUser(String id, String email, String phone, String firstName, String lastName);

    /**
     * 更新用户密码
     *
     * @param id       用户ID
     * @param password 新密码
     * @return 更新后的用户
     */
    User updatePassword(String id, String password);

    /**
     * 激活用户
     *
     * @param id 用户ID
     * @return 激活后的用户
     */
    User activateUser(String id);

    /**
     * 禁用用户
     *
     * @param id 用户ID
     * @return 禁用后的用户
     */
    User deactivateUser(String id);

    /**
     * 锁定用户
     *
     * @param id 用户ID
     * @return 锁定后的用户
     */
    User lockUser(String id);

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    void deleteUser(String id);

    /**
     * 为用户分配角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 更新后的用户
     */
    User assignRoleToUser(String userId, String roleId);

    /**
     * 从用户移除角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 更新后的用户
     */
    User removeRoleFromUser(String userId, String roleId);
}