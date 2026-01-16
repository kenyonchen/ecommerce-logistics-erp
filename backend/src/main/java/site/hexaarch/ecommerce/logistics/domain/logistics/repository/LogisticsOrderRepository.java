package site.hexaarch.ecommerce.logistics.domain.logistics.repository;

import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

import java.util.List;
import java.util.Optional;

/**
 * 物流单仓储接口，负责持久化物流单聚合和提供物流单的访问方法。
 *
 * @author kenyon
 */
public interface LogisticsOrderRepository {
    /**
     * 保存物流单。
     *
     * @param logisticsOrder 物流单聚合
     * @return 保存后的物流单聚合
     */
    LogisticsOrder save(LogisticsOrder logisticsOrder);

    /**
     * 根据ID查找物流单。
     *
     * @param id 物流单ID
     * @return 物流单聚合，如果不存在则返回Optional.empty()
     */
    Optional<LogisticsOrder> findById(String id);

    /**
     * 根据订单ID查找物流单。
     *
     * @param orderId 订单ID
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findByOrderId(String orderId);

    /**
     * 根据物流渠道ID查找物流单。
     *
     * @param channelId 物流渠道ID
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findByLogisticsChannelId(String channelId);

    /**
     * 根据物流状态查找物流单。
     *
     * @param status 物流状态
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findByLogisticsStatus(LogisticsStatus status);

    /**
     * 查找所有物流单。
     *
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findAll();

    /**
     * 删除物流单。
     *
     * @param id 物流单ID
     */
    void delete(String id);
}