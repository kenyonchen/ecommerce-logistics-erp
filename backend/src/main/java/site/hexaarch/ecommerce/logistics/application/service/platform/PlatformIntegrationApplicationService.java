package site.hexaarch.ecommerce.logistics.application.service.platform;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.service.PlatformIntegrationService;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;
import java.util.UUID;

/**
 * 平台集成应用服务
 *
 * @author kenyon
 */
@Service
public class PlatformIntegrationApplicationService {

    private final PlatformIntegrationService platformIntegrationService;
    private final DomainEventPublisher domainEventPublisher;

    @Autowired
    public PlatformIntegrationApplicationService(PlatformIntegrationService platformIntegrationService, DomainEventPublisher domainEventPublisher) {
        this.platformIntegrationService = platformIntegrationService;
        this.domainEventPublisher = domainEventPublisher;
    }

    public PlatformIntegration createPlatformIntegration(PlatformIntegration platformIntegration) {
        PlatformIntegration integration = platformIntegrationService.createPlatformIntegration(platformIntegration);
        // 发布平台集成聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(integration);
        return integration;
    }

    public PlatformIntegration getPlatformIntegrationById(UUID id) {
        return platformIntegrationService.getPlatformIntegrationById(id);
    }

    public List<PlatformIntegration> getAllPlatformIntegrations() {
        return platformIntegrationService.getAllPlatformIntegrations();
    }

    public PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration) {
        return platformIntegrationService.updatePlatformIntegration(id, platformIntegration);
    }

    public void deletePlatformIntegration(UUID id) {
        platformIntegrationService.deletePlatformIntegration(id);
    }

    public PlatformIntegration activatePlatformIntegration(UUID id) {
        return platformIntegrationService.activatePlatformIntegration(id);
    }

    public PlatformIntegration deactivatePlatformIntegration(UUID id) {
        return platformIntegrationService.deactivatePlatformIntegration(id);
    }

    public void syncOrdersFromPlatform(UUID platformId) {
        platformIntegrationService.syncOrdersFromPlatform(platformId);
    }

    public void syncProductsFromPlatform(UUID platformId) {
        platformIntegrationService.syncProductsFromPlatform(platformId);
    }

    public void syncInventoryToPlatform(UUID platformId) {
        platformIntegrationService.syncInventoryToPlatform(platformId);
    }
}
