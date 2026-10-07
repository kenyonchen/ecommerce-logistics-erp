package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseJpaEntity;

/**
 * 店铺仓库JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface ShopWarehouseJpaRepository extends JpaRepository<ShopWarehouseJpaEntity, String> {

    Optional<ShopWarehouseJpaEntity> findByTenantIdAndPlatformAndPlatformWarehouseId(
            String tenantId, String platform, String platformWarehouseId);

    List<ShopWarehouseJpaEntity> findByTenantId(String tenantId);

    List<ShopWarehouseJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    List<ShopWarehouseJpaEntity> findByLocalWarehouseId(String localWarehouseId);
}
