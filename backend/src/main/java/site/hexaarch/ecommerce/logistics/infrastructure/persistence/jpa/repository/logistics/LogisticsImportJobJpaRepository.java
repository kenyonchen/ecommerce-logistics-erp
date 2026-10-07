package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsImportJobJpaEntity;

/**
 * 物流单导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface LogisticsImportJobJpaRepository
        extends JpaRepository<LogisticsImportJobJpaEntity, String>,
        JpaSpecificationExecutor<LogisticsImportJobJpaEntity> {
}
