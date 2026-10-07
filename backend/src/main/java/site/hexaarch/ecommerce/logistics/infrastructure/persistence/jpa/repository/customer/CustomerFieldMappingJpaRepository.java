package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerFieldMappingJpaEntity;

/**
 * 第三方平台客户字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface CustomerFieldMappingJpaRepository extends JpaRepository<CustomerFieldMappingJpaEntity, String> {

    List<CustomerFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
