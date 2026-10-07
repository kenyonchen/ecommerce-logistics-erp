package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinanceImportJobJpaEntity;

/**
 * 财务导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface FinanceImportJobJpaRepository extends JpaRepository<FinanceImportJobJpaEntity, String>,
        JpaSpecificationExecutor<FinanceImportJobJpaEntity> {
}
