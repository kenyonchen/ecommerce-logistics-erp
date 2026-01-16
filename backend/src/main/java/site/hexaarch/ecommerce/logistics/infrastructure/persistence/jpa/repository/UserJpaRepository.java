package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.UserJpaEntity;

import java.util.List;
import java.util.Optional;

/**
 * 用户JPA仓库接口，用于操作用户JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {
    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    Optional<UserJpaEntity> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @return 用户实体
     */
    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * 根据租户ID查找用户列表
     *
     * @param tenantId 租户ID
     * @return 用户实体列表
     */
    List<UserJpaEntity> findByTenantId(String tenantId);
}
