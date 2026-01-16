package site.hexaarch.ecommerce.logistics.domain.platform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台集成服务实现类
 *
 * @author kenyon
 */
@Service
public class PlatformIntegrationServiceImpl implements PlatformIntegrationService {

    private final PlatformIntegrationRepository platformIntegrationRepository;

    @Autowired
    public PlatformIntegrationServiceImpl(PlatformIntegrationRepository platformIntegrationRepository) {
        this.platformIntegrationRepository = platformIntegrationRepository;
    }

    @Override
    public PlatformIntegration createPlatformIntegration(PlatformIntegration platformIntegration) {
        platformIntegration.create();
        return platformIntegrationRepository.save(platformIntegration);
    }

    @Override
    public PlatformIntegration getPlatformIntegrationById(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        return optionalPlatform.orElse(null);
    }

    @Override
    public List<PlatformIntegration> getAllPlatformIntegrations() {
        return platformIntegrationRepository.findAll();
    }

    @Override
    public PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration) {
        Optional<PlatformIntegration> optionalExisting = platformIntegrationRepository.findById(id);
        if (optionalExisting.isPresent()) {
            PlatformIntegration existing = optionalExisting.get();
            PlatformIntegration updated = PlatformIntegration.builder()
                    .id(existing.getId())
                    .platformName(platformIntegration.getPlatformName())
                    .apiKey(platformIntegration.getApiKey())
                    .apiSecret(platformIntegration.getApiSecret())
                    .storeUrl(platformIntegration.getStoreUrl())
                    .status(platformIntegration.getStatus())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(platformIntegration.getUpdatedAt())
                    .build();
            return platformIntegrationRepository.save(updated);
        }
        return null;
    }

    @Override
    public void deletePlatformIntegration(UUID id) {
        platformIntegrationRepository.deleteById(id);
    }

    @Override
    public PlatformIntegration activatePlatformIntegration(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        if (optionalPlatform.isPresent()) {
            PlatformIntegration platform = optionalPlatform.get();
            platform.activate();
            return platformIntegrationRepository.save(platform);
        }
        return null;
    }

    @Override
    public PlatformIntegration deactivatePlatformIntegration(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        if (optionalPlatform.isPresent()) {
            PlatformIntegration platform = optionalPlatform.get();
            platform.deactivate();
            return platformIntegrationRepository.save(platform);
        }
        return null;
    }

    @Override
    public void syncOrdersFromPlatform(UUID platformId) {
        // Implementation for syncing orders from platform
        // This would typically involve calling the platform's API to fetch orders
        // and then processing them into the system
        System.out.println("Syncing orders from platform with id: " + platformId);
    }

    @Override
    public void syncProductsFromPlatform(UUID platformId) {
        // Implementation for syncing products from platform
        // This would typically involve calling the platform's API to fetch products
        // and then processing them into the system
        System.out.println("Syncing products from platform with id: " + platformId);
    }

    @Override
    public void syncInventoryToPlatform(UUID platformId) {
        // Implementation for syncing inventory to platform
        // This would typically involve fetching inventory from the system
        // and then updating it on the platform via API calls
        System.out.println("Syncing inventory to platform with id: " + platformId);
    }
}
