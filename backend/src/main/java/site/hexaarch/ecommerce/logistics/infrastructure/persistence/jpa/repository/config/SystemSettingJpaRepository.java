package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.config.SystemSettingJpaEntity;

/**
 * 平台配置JPA仓库接口。
 *
 * @author kenyon
 */
@Repository
public interface SystemSettingJpaRepository extends JpaRepository<SystemSettingJpaEntity, String> {
}
