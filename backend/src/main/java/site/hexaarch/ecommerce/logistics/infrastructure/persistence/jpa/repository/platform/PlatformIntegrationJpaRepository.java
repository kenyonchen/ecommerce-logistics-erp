package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformStatusJpaEntity;

/**
 * 平台集成JPA仓库接口
 *
 * @author kenyon
 */
public interface PlatformIntegrationJpaRepository extends JpaRepository<PlatformIntegrationJpaEntity, UUID> {

    List<PlatformIntegrationJpaEntity> findByStatus(PlatformStatusJpaEntity status);

    /**
     * 统计租户下的平台集成总数（首页概览用）
     */
    long countByTenantId(String tenantId);

    /**
     * 按租户查询平台集成列表（租户端只读接口的隔离入口）。
     * <p>
     * {@code findAll()} 不带租户条件，仅供平台管理员跨租户查看；租户侧读取必须走本方法。
     * </p>
     */
    List<PlatformIntegrationJpaEntity> findByTenantId(String tenantId);
}
