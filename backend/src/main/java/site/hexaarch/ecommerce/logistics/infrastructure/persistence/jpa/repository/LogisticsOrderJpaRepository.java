package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsOrderJpaEntity;

import java.util.Optional;

/**
 * 物流单JPA仓库接口，用于操作物流单JPA实体。
 *
 * @author kenyon
 */
public interface LogisticsOrderJpaRepository extends JpaRepository<LogisticsOrderJpaEntity, String> {
    /**
     * 根据物流单号查找物流单
     */
    Optional<LogisticsOrderJpaEntity> findByLogisticsOrderNumber(String logisticsOrderNumber);

    /**
     * 根据订单ID查找物流单
     */
    Optional<LogisticsOrderJpaEntity> findByOrderId(String orderId);
}
