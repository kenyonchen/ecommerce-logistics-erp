package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderFieldMappingJpaEntity;

/**
 * 第三方平台订单字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface OrderFieldMappingJpaRepository extends JpaRepository<OrderFieldMappingJpaEntity, String> {

    List<OrderFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
