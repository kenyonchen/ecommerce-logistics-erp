package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderStatusJpaEntity;

import java.util.Collection;
import java.util.Optional;

/**
 * 订单JPA仓库接口，用于操作订单JPA实体。
 *
 * @author kenyon
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {
    /**
     * 根据订单号查找订单
     */
    Optional<OrderJpaEntity> findByOrderNumber(String orderNumber);

    /**
     * 根据客户ID查找订单
     */
    Collection<OrderJpaEntity> findByCustomerId(String customerId);

    /**
     * 根据订单状态查找订单
     */
    Collection<OrderJpaEntity> findByStatus(OrderStatusJpaEntity status);
}
