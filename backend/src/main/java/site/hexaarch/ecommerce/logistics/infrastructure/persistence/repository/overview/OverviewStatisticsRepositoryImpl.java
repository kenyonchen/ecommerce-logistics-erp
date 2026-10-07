package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.overview;

import lombok.RequiredArgsConstructor;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.overview.repository.OverviewStatisticsRepository;
import site.hexaarch.ecommerce.logistics.domain.overview.statistics.SystemOverviewStatistics;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer.CustomerJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinancialTransactionJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.platform.PlatformIntegrationJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product.ProductJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase.PurchaseOrderJpaRepository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.WarehouseJpaRepository;

/**
 * 系统概览统计仓储实现，直接用聚合查询跨域取数，不加载明细到内存。
 *
 * @author kenyon
 */
@RequiredArgsConstructor
public class OverviewStatisticsRepositoryImpl implements OverviewStatisticsRepository {
    private final OrderJpaRepository orderJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final PurchaseOrderJpaRepository purchaseOrderJpaRepository;
    private final PlatformIntegrationJpaRepository platformIntegrationJpaRepository;
    private final FinancialTransactionJpaRepository financialTransactionJpaRepository;
    private final OrderMapper orderMapper;

    @Override
    public SystemOverviewStatistics loadStatistics(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        String key = tenantId.trim();
        return new SystemOverviewStatistics(
                orderJpaRepository.countByTenantId(key),
                loadOrderStatusCounts(key),
                productJpaRepository.countByTenantId(key),
                warehouseJpaRepository.countByTenantId(key),
                customerJpaRepository.countByTenantId(key),
                purchaseOrderJpaRepository.countByTenantId(key),
                platformIntegrationJpaRepository.countByTenantId(key),
                loadRevenue(key)
        );
    }

    private Map<OrderStatus, Long> loadOrderStatusCounts(String tenantId) {
        List<OrderJpaRepository.OrderStatusCount> rows = orderJpaRepository.countGroupByStatusForTenant(tenantId);
        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        if (rows == null) {
            return result;
        }
        for (OrderJpaRepository.OrderStatusCount row : rows) {
            if (row == null || row.getStatus() == null || row.getTotal() == null) {
                continue;
            }
            result.put(orderMapper.orderStatusFromJpaEntity(row.getStatus()), row.getTotal());
        }
        return result;
    }

    /**
     * 总收入口径：财务流水表中「收入 + 成功」的金额合计。
     * <p>
     * 订单表的 total_amount 列在领域映射时被忽略写入（恒为 null），不能作为金额来源。
     * </p>
     */
    private BigDecimal loadRevenue(String tenantId) {
        BigDecimal sum = financialTransactionJpaRepository.sumAmountByTenantIdAndTypeAndStatus(
                tenantId, TransactionType.INCOME.getCode(), TransactionStatus.SUCCESS.getCode());
        return sum == null ? BigDecimal.ZERO : sum;
    }
}