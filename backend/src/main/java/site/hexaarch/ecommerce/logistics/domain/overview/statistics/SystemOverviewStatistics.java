package site.hexaarch.ecommerce.logistics.domain.overview.statistics;

import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 系统概览统计数据，首页「系统概览」一次性读取，避免前端多次分页查询拼装。
 * <p>
 * 全部为租户维度，仓储层已完成租户过滤。
 * </p>
 *
 * @author kenyon
 */
public record SystemOverviewStatistics(
        long totalOrders,
        Map<OrderStatus, Long> orderStatusCounts,
        long totalProducts,
        long totalWarehouses,
        long totalCustomers,
        long totalPurchases,
        long totalPlatformIntegrations,
        BigDecimal totalRevenue) {

    /**
     * 规范化状态计数映射，保证不可变且非 null。
     */
    public SystemOverviewStatistics {
        orderStatusCounts = normalize(orderStatusCounts);
        totalRevenue = totalRevenue == null ? BigDecimal.ZERO : totalRevenue;
    }

    private static Map<OrderStatus, Long> normalize(Map<OrderStatus, Long> source) {
        Map<OrderStatus, Long> target = new EnumMap<>(OrderStatus.class);
        if (source != null) {
            source.forEach((status, count) -> {
                if (status != null && count != null && count > 0) {
                    target.put(status, count);
                }
            });
        }
        return Collections.unmodifiableMap(target);
    }

    /**
     * 取指定状态的订单数量，未出现过的状态返回 0。
     */
    public long countOf(OrderStatus status) {
        return orderStatusCounts.getOrDefault(status, 0L);
    }
}