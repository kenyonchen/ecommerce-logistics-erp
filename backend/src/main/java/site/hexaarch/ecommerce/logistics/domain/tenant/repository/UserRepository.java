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
     * 删除用户
     *
     * @param id 用户ID
     */
    void deleteById(String id);
}