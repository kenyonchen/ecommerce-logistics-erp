package site.hexaarch.ecommerce.logistics.domain.overview.repository;

import site.hexaarch.ecommerce.logistics.domain.overview.statistics.SystemOverviewStatistics;

/**
 * 系统概览统计仓储，向首页概览提供跨域聚合读模型。
 * <p>
 * 属于查询型（CQRS 读侧）仓储，只读不写；租户过滤由实现层在数据库侧完成。
 * </p>
 *
 * @author kenyon
 */
public interface OverviewStatisticsRepository {

    /**
     * 一次性读取指定租户的概览统计数据。
     *
     * @param tenantId 租户ID
     * @return 统计数据，无数据时各项为 0
     */
    SystemOverviewStatistics loadStatistics(String tenantId);
}