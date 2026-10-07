package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinanceFieldMappingJpaEntity;

import java.util.List;

/**
 * 第三方平台财务字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface FinanceFieldMappingJpaRepository extends JpaRepository<FinanceFieldMappingJpaEntity, String> {

    List<FinanceFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
