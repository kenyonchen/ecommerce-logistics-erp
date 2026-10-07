package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsFieldMappingJpaEntity;

/**
 * 物流字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface LogisticsFieldMappingJpaRepository
        extends JpaRepository<LogisticsFieldMappingJpaEntity, String> {

    List<LogisticsFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    @Modifying
    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
