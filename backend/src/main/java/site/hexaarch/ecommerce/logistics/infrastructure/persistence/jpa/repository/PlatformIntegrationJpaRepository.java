package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.PlatformStatusJpaEntity;

import java.util.List;
import java.util.UUID;

/**
 * 平台集成JPA仓库接口
 *
 * @author kenyon
 */
public interface PlatformIntegrationJpaRepository extends JpaRepository<PlatformIntegrationJpaEntity, UUID> {

    List<PlatformIntegrationJpaEntity> findByStatus(PlatformStatusJpaEntity status);
}
