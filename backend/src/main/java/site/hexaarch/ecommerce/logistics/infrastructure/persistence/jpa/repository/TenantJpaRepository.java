package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.TenantJpaEntity;

import java.util.Optional;

/**
 * 租户JPA仓库接口，用于操作租户JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, String> {
    /**
     * 根据名称查找租户
     *
     * @param name 租户名称
     * @return 租户实体
     */
    Optional<TenantJpaEntity> findByName(String name);

    /**
     * 根据联系邮箱查找租户
     *
     * @param email 联系邮箱
     * @return 租户实体
     */
    Optional<TenantJpaEntity> findByContactEmail(String email);
}
