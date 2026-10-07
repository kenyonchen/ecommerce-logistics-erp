package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerImportJobJpaEntity;

/**
 * 客户导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface CustomerImportJobJpaRepository extends JpaRepository<CustomerImportJobJpaEntity, String>,
        JpaSpecificationExecutor<CustomerImportJobJpaEntity> {
}
