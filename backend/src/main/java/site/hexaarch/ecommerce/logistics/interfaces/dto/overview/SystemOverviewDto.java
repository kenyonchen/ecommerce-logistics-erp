package site.hexaarch.ecommerce.logistics.interfaces.dto.overview;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统概览DTO，供首页「系统概览」一次性渲染关键指标与最近订单。
 *
 * @author kenyon
 */
@Data
@Schema(description = "系统概览")
public class SystemOverviewDto {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "订单总数")
    private long totalOrders;

    @Schema(description = "待处理订单数")
    private long pendingOrders;

    @Schema(description = "处理中订单数")
    private long processingOrders;

    @Schema(description = "已确认订单数")
    private long confirmedOrders;

    @Schema(description = "已发货订单数")
    private long shippedOrders;

    @Schema(description = "已完成订单数")
    private long completedOrders;

    @Schema(description = "已取消订单数")
    private long cancelledOrders;

    @Schema(description = "产品总数")
    private long totalProducts;

    @Schema(description = "仓库总数")
    private long totalWarehouses;

    @Schema(description = "客户总数")
    private long totalCustomers;

    @Schema(description = "采购单总数")
    private long totalPurchases;

    @Schema(description = "平台集成总数")
    private long totalPlatformIntegrations;

    @Schema(description = "总收入，口径为财务流水「收入 + 成功」的金额合计")
    private BigDecimal totalRevenue;

    @Schema(description = "最近订单，按创建时间倒序")
    private List<OrderDto> recentOrders;
}