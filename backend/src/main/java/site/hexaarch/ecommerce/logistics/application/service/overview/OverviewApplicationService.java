package site.hexaarch.ecommerce.logistics.application.service.overview;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderQuery;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.overview.repository.OverviewStatisticsRepository;
import site.hexaarch.ecommerce.logistics.domain.overview.statistics.SystemOverviewStatistics;

import java.util.List;

/**
 * 系统概览应用服务，为首页「系统概览」提供聚合读模型。
 * <p>
 * 注意：租户隔离切面按参数名 {@code tenantId} 校验，本服务的所有公开方法
 * 必须把 {@code tenantId} 作为第一个参数，否则切面不会生效。
 * </p>
 *
 * @author kenyon
 */
@Service
public class OverviewApplicationService {

    /**
     * 未携带租户上下文时的回退租户，与 OrderApplicationService 保持一致。
     */
    public static final String DEFAULT_TENANT_ID = "default-tenant";

    /**
     * 最近订单条数上限，防止前端传入过大的 limit 拖垮查询。
     */
    public static final int MAX_RECENT_ORDER_LIMIT = 20;

    private final OverviewStatisticsRepository overviewStatisticsRepository;
    private final OrderRepository orderRepository;

    // 手动添加构造函数，避免Lombok注解问题
    public OverviewApplicationService(OverviewStatisticsRepository overviewStatisticsRepository,
                                      OrderRepository orderRepository) {
        this.overviewStatisticsRepository = overviewStatisticsRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 读取概览统计数据（订单/产品/仓库/客户/采购/平台集成/收入）。
     *
     * @param tenantId 租户ID
     * @return 统计数据，无数据时各项为 0
     */
    @Transactional(readOnly = true)
    public SystemOverviewStatistics loadStatistics(String tenantId) {
        return overviewStatisticsRepository.loadStatistics(requireTenantId(tenantId));
    }

    /**
     * 读取最近订单，按创建时间倒序。
     *
     * @param tenantId 租户ID
     * @param limit    返回条数，非法值按默认 5 处理，上限 {@link #MAX_RECENT_ORDER_LIMIT}
     * @return 最近订单领域对象列表
     */
    @Transactional(readOnly = true)
    public List<Order> findRecentOrders(String tenantId, int limit) {
        int safeLimit = normalizeLimit(limit);
        return orderRepository.search(OrderQuery.of(requireTenantId(tenantId)), 0, safeLimit).getContent();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 5;
        }
        return Math.min(limit, MAX_RECENT_ORDER_LIMIT);
    }

    private String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        return tenantId.trim();
    }
}