package site.hexaarch.ecommerce.logistics.domain.tenant.repository;

import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口
 *
 * @author kenyon
 */
public interface UserRepository {
    /**
     * 保存用户
     *
     * @param user 用户实体
     * @return 保存后的用户
     */
    User save(User user);

    /**
     * 根据ID查找用户
     *
     * @param id 用户ID
     * @return 用户
     */
    Optional<User> findById(String id);

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据租户ID查找用户列表
     *
     * @param tenantId 租户ID
     * @return 用户列表
     */
    List<User> findByTenantId(String tenantId);

    /**
     * 全平台用户列表，仅供平台管理员的跨租户概览使用
     * （调用点必须先把住 ROLE_PLATFORM_ADMIN 门槛，见 {@code UserController#getUsers}）
     *
     * @return 全部用户
     */
    List<User> findAll();

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    void deleteById(String id);
}