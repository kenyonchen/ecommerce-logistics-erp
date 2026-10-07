package site.hexaarch.ecommerce.logistics.domain.order.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓储接口，负责持久化订单聚合和提供订单的访问方法。
 *
 * @author kenyon
 */
public interface OrderRepository {
    /**
     * 保存订单。
     *
     * @param order 订单聚合
     * @return 保存后的订单聚合
     */
    Order save(Order order);

    /**
     * 根据ID查找订单。
     *
     * @param id 订单ID
     * @return 订单聚合，如果不存在则返回Optional.empty()
     */
    Optional<Order> findById(String id);

    /**
     * 根据客户ID查找订单。
     *
     * @param customerId 客户ID
     * @return 订单聚合列表
     */
    List<Order> findByCustomerId(String customerId);

    /**
     * 根据订单状态查找订单。
     *
     * @param status 订单状态
     * @return 订单聚合列表
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * 查找所有订单。
     *
     * @return 订单聚合列表
     */
    List<Order> findAll();

    /**
     * 删除订单。
     *
     * @param id 订单ID
     */
    void delete(String id);

    /**
     * 根据订单号查找订单（全局唯一）。
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * 按「租户 + 平台 + 平台订单号」查找订单，用于导入幂等去重。
     */
    Optional<Order> findByPlatformOrderId(String tenantId, String platform, String platformOrderId);

    /**
     * 判断「租户 + 平台 + 平台订单号」是否已存在。
     */
    boolean existsByPlatformOrderId(String tenantId, String platform, String platformOrderId);

    /**
     * 分页 + 条件查询订单，按创建时间倒序。
     */
    PageResult<Order> search(OrderQuery query, int page, int size);
}
