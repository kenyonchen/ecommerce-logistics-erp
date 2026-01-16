package site.hexaarch.ecommerce.logistics.domain.platform.repository;

import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台集成仓储接口
 *
 * @author kenyon
 */
public interface PlatformIntegrationRepository {

    Optional<PlatformIntegration> findById(UUID id);

    PlatformIntegration save(PlatformIntegration platformIntegration);

    void deleteById(UUID id);

    List<PlatformIntegration> findAll();

    List<PlatformIntegration> findByStatusActive();
}
