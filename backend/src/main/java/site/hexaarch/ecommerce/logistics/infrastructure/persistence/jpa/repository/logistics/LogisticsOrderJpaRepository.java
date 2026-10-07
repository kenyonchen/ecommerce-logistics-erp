package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsOrderJpaEntity;

/**
 * 物流单JPA仓库接口，用于操作物流单JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface LogisticsOrderJpaRepository extends JpaRepository<LogisticsOrderJpaEntity, String>,
        JpaSpecificationExecutor<LogisticsOrderJpaEntity> {

    /**
     * 根据物流单号查找物流单
     */
    Optional<LogisticsOrderJpaEntity> findByLogisticsOrderNumber(String logisticsOrderNumber);

    /**
     * 根据订单ID查找物流单
     */
    List<LogisticsOrderJpaEntity> findByOrderId(String orderId);

    /**
     * 根据运单号查找物流单
     */
    List<LogisticsOrderJpaEntity> findByTrackingNumber(String trackingNumber);

    /**
     * 根据物流渠道ID查找物流单
     */
    List<LogisticsOrderJpaEntity> findByLogisticsChannelId(String channelId);

    /**
     * 按平台与平台物流单号查找，用于导入幂等
     */
    Optional<LogisticsOrderJpaEntity> findFirstByTenantIdAndPlatformAndExternalLogisticsId(
            String tenantId, String platform, String externalLogisticsId);

    /**
     * 统计租户下物流单数量
     */
    long countByTenantId(String tenantId);
}
