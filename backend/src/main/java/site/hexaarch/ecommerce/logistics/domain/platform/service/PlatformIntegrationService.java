package site.hexaarch.ecommerce.logistics.domain.platform.service;

import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;

import java.util.List;
import java.util.UUID;

/**
 * 平台集成服务接口
 *
 * @author kenyon
 */
public interface PlatformIntegrationService {

    PlatformIntegration createPlatformIntegration(PlatformIntegration platformIntegration);

    PlatformIntegration getPlatformIntegrationById(UUID id);

    List<PlatformIntegration> getAllPlatformIntegrations();

    /**
     * 按租户查询平台集成列表，供租户侧只读接口使用。
     *
     * @param tenantId 租户ID
     * @return 该租户下的平台集成，入参为空时返回空列表（不做跨租户兜底）
     */
    List<PlatformIntegration> getPlatformIntegrationsByTenant(String tenantId);

    PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration);

    void deletePlatformIntegration(UUID id);

    PlatformIntegration activatePlatformIntegration(UUID id);

    PlatformIntegration deactivatePlatformIntegration(UUID id);

    /**
     * 商品同步已改由「商品导入应用服务」实现（拉取 -> 字段映射 -> 批量导入 -> 结果反馈），
     * 该方法不再承载同步逻辑，仅在同步前后更新平台连接状态。
     *
     * @return 平台连接的更新时间
     */
    java.time.LocalDateTime markProductsSynced(UUID platformId);

    /**
     * 订单同步已改由「订单导入应用服务」实现（拉取 -> 字段映射 -> 批量导入 -> 结果反馈），
     * 该方法不再承载同步逻辑，仅在同步前后更新平台连接状态。
     *
     * @return 平台连接的更新时间
     */
    java.time.LocalDateTime markOrdersSynced(UUID platformId);

    /**
     * 将系统内库存推送到平台店铺（POST {storeUrl}/inventory，鉴权头 X-API-Key / X-API-Secret）。
     *
     * @param platformId 平台集成ID
     * @return 推送结果（SKU明细与时间）
     */
    InventorySyncResult syncInventoryToPlatform(UUID platformId);
}
