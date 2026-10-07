package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderImportJobJpaEntity;

/**
 * 订单导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface OrderImportJobJpaRepository extends JpaRepository<OrderImportJobJpaEntity, String>,
        JpaSpecificationExecutor<OrderImportJobJpaEntity> {
}
