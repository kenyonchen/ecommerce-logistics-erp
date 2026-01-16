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

    PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration);

    void deletePlatformIntegration(UUID id);

    PlatformIntegration activatePlatformIntegration(UUID id);

    PlatformIntegration deactivatePlatformIntegration(UUID id);

    void syncOrdersFromPlatform(UUID platformId);

    void syncProductsFromPlatform(UUID platformId);

    void syncInventoryToPlatform(UUID platformId);
}
