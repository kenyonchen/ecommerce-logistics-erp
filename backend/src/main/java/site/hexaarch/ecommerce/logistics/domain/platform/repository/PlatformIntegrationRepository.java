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

    /**
     * 按租户查询平台集成列表。
     * <p>
     * 租户侧读取必须走本方法，{@link #findAll()} 不做租户过滤、仅供平台管理员跨租户查看。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 该租户下的平台集成
     */
    List<PlatformIntegration> findByTenantId(String tenantId);

    PlatformIntegration save(PlatformIntegration platformIntegration);

    void deleteById(UUID id);

    List<PlatformIntegration> findAll();

    List<PlatformIntegration> findByStatusActive();
}
