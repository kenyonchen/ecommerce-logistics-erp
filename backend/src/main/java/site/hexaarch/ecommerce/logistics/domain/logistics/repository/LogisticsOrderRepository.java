package site.hexaarch.ecommerce.logistics.domain.logistics.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
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
     * 根据物流单号查找物流单
     */
    Optional<LogisticsOrder> findByLogisticsOrderNumber(String logisticsOrderNumber);

    /**
     * 根据订单ID查找物流单。
     *
     * @param orderId 订单ID
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findByOrderId(String orderId);

    /**
     * 根据运单号查找物流单
     */
    List<LogisticsOrder> findByTrackingNumber(String trackingNumber);

    /**
     * 按租户+平台+平台物流单号查找，用于导入幂等
     */
    Optional<LogisticsOrder> findByPlatformExternalId(String tenantId, String platform, String externalLogisticsId);

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
     * 条件分页查询物流单
     *
     * @param tenantId      租户ID
     * @param trackingNumber 运单号（模糊）
     * @param orderId       订单ID
     * @param channelId     物流渠道ID
     * @param status        物流状态
     * @param keyword       关键字（物流单号/运单号/订单号）
     * @param startDate     创建时间起（yyyy-MM-dd）
     * @param endDate       创建时间止（yyyy-MM-dd）
     * @param page          页码，从0开始
     * @param size          每页条数
     */
    PageResult<LogisticsOrder> search(String tenantId, String trackingNumber, String orderId, String channelId,
                                      LogisticsStatus status, String keyword, String startDate, String endDate,
                                      int page, int size);

    /**
     * 查找所有物流单。
     *
     * @return 物流单聚合列表
     */
    List<LogisticsOrder> findAll();

    /**
     * 统计租户下的物流单总数
     */
    long countByTenantId(String tenantId);

    /**
     * 删除物流单。
     *
     * @param id 物流单ID
     */
    void delete(String id);
}