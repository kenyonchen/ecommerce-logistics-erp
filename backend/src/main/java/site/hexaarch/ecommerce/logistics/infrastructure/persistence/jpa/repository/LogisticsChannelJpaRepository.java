package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsChannelJpaEntity;

import java.util.Collection;
import java.util.Optional;

/**
 * 物流渠道JPA仓库接口，用于操作物流渠道JPA实体。
 *
 * @author kenyon
 */
public interface LogisticsChannelJpaRepository extends JpaRepository<LogisticsChannelJpaEntity, String> {
    /**
     * 根据渠道编码查找物流渠道
     */
    Optional<LogisticsChannelJpaEntity> findByChannelCode(String channelCode);

    /**
     * 根据渠道名称查找物流渠道
     */
    Optional<LogisticsChannelJpaEntity> findByChannelName(String channelName);

    /**
     * 查询所有可用的物流渠道
     *
     * @return
     */
    Collection<LogisticsChannelJpaEntity> findByActiveTrue();
}
