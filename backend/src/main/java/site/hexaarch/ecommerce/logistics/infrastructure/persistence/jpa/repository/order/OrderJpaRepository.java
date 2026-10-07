package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderStatusJpaEntity;

/**
 * 订单JPA仓库接口，用于操作订单JPA实体。
 *
 * @author kenyon
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String>, JpaSpecificationExecutor<OrderJpaEntity> {
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

    /**
     * 导入幂等去重：按「租户 + 平台 + 平台订单号」判断
     */
    boolean existsByTenantIdAndPlatformAndPlatformOrderId(String tenantId, String platform, String platformOrderId);

    /**
     * 导入幂等去重：按「租户 + 平台 + 平台订单号」查找
     */
    Optional<OrderJpaEntity> findByTenantIdAndPlatformAndPlatformOrderId(String tenantId, String platform, String platformOrderId);

    /**
     * 统计租户下的订单总数（首页概览用）。
     */
    long countByTenantId(String tenantId);

    /**
     * 按状态分组统计订单数量（首页概览用，一次查询取全部状态）。
     * <p>
     * 注意：{@code orders.total_amount} 列在领域映射中被显式忽略写入，恒为 null，
     * 因此订单金额不能落库求和，金额统计统一走财务流水表。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 状态-数量投影列表
     */
    @Query("SELECT o.status AS status, COUNT(o) AS total FROM OrderJpaEntity o WHERE o.tenantId = :tenantId GROUP BY o.status")
    List<OrderStatusCount> countGroupByStatusForTenant(@Param("tenantId") String tenantId);

    /**
     * 订单状态分组统计投影。
     */
    interface OrderStatusCount {
        OrderStatusJpaEntity getStatus();

        Long getTotal();
    }
}