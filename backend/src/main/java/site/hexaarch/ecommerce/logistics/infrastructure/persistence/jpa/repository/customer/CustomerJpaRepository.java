package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerJpaEntity;

/**
 * 客户JPA仓储接口
 *
 * @author kenyon
 */
public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, String>,
        JpaSpecificationExecutor<CustomerJpaEntity> {

    Optional<CustomerJpaEntity> findByEmail(String email);

    Optional<CustomerJpaEntity> findByTenantIdAndSourcePlatformAndExternalBuyerId(
            String tenantId, String sourcePlatform, String externalBuyerId);

    long countByStatus(CustomerStatus status);

    /**
     * 统计租户下的客户总数（首页概览用）
     */
    long countByTenantId(String tenantId);
}
