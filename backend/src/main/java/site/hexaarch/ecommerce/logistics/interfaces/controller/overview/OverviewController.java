package site.hexaarch.ecommerce.logistics.interfaces.controller.overview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.overview.OverviewApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.overview.statistics.SystemOverviewStatistics;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.overview.SystemOverviewDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统概览控制器，为前端首页提供一次性聚合读接口。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/overview")
@Tag(name = "系统概览", description = "首页概览聚合统计接口")
public class OverviewController {
    private static final String HAS_ORDER_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('order:read')";

    private final OverviewApplicationService overviewApplicationService;
    private final OrderMapper orderMapper;

    public OverviewController(OverviewApplicationService overviewApplicationService, OrderMapper orderMapper) {
        this.overviewApplicationService = overviewApplicationService;
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "获取系统概览",
            description = "一次性返回订单/产品/仓库/客户/采购/平台集成/收入等关键指标及最近订单，避免前端多次分页查询拼装")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping
    public Result<SystemOverviewDto> getSystemOverview(
            @Parameter(description = "租户ID，不传则取当前登录租户") @RequestParam(required = false) String tenantId,
            @Parameter(description = "最近订单条数，默认5，上限20") @RequestParam(required = false, defaultValue = "5") Integer recentOrderLimit) {
        String resolvedTenantId = resolveTenant(tenantId);
        int limit = recentOrderLimit == null ? 5 : recentOrderLimit;

        SystemOverviewStatistics statistics = overviewApplicationService.loadStatistics(resolvedTenantId);
        List<Order> recentOrders = overviewApplicationService.findRecentOrders(resolvedTenantId, limit);

        SystemOverviewDto dto = new SystemOverviewDto();
        dto.setTenantId(resolvedTenantId);
        dto.setTotalOrders(statistics.totalOrders());
        dto.setPendingOrders(statistics.countOf(OrderStatus.PENDING));
        dto.setProcessingOrders(statistics.countOf(OrderStatus.PROCESSING));
        dto.setConfirmedOrders(statistics.countOf(OrderStatus.CON_FIRMED));
        dto.setShippedOrders(statistics.countOf(OrderStatus.SHIPPED));
        dto.setCompletedOrders(statistics.countOf(OrderStatus.COMPLETED));
        dto.setCancelledOrders(statistics.countOf(OrderStatus.CANCELLED));
        dto.setTotalProducts(statistics.totalProducts());
        dto.setTotalWarehouses(statistics.totalWarehouses());
        dto.setTotalCustomers(statistics.totalCustomers());
        dto.setTotalPurchases(statistics.totalPurchases());
        dto.setTotalPlatformIntegrations(statistics.totalPlatformIntegrations());
        dto.setTotalRevenue(statistics.totalRevenue());
        dto.setRecentOrders(toRecentOrderDtos(recentOrders));
        return Result.success(dto);
    }

    private List<OrderDto> toRecentOrderDtos(List<Order> recentOrders) {
        if (recentOrders == null || recentOrders.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderDto> dtos = orderMapper.toDtoList(recentOrders);
        if (dtos == null) {
            return new ArrayList<>();
        }
        for (int i = 0; i < dtos.size() && i < recentOrders.size(); i++) {
            dtos.get(i).applyStatusDerivedFields(recentOrders.get(i));
        }
        return dtos;
    }

    /**
     * 租户解析：优先取请求参数，其次取租户上下文，最后回退默认租户
     */
    private String resolveTenant(String tenantId) {
        String requested = TenantContext.normalizeRequested(tenantId);
        if (requested != null) {
            return requested;
        }
        if (TenantContext.hasTenantId()) {
            return TenantContext.getTenantId();
        }
        return OverviewApplicationService.DEFAULT_TENANT_ID;
    }
}