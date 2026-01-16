package site.hexaarch.ecommerce.logistics.domain.order.repository;

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
}