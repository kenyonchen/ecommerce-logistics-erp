package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.RoleJpaEntity;

import java.util.List;
import java.util.Optional;

/**
 * 角色JPA仓库接口，用于操作角色JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, String> {
    /**
     * 根据名称和租户ID查找角色
     *
     * @param name     角色名称
     * @param tenantId 租户ID
     * @return 角色实体
     */
    Optional<RoleJpaEntity> findByNameAndTenantId(String name, String tenantId);

    /**
     * 根据租户ID查找角色列表
     *
     * @param tenantId 租户ID
     * @return 角色实体列表
     */
    List<RoleJpaEntity> findByTenantId(String tenantId);
}
