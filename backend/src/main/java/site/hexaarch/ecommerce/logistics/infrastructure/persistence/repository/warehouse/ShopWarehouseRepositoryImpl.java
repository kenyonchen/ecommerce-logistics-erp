package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseJpaEntity;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.ShopWarehouseJpaRepository;

/**
 * 店铺仓库仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ShopWarehouseRepositoryImpl implements ShopWarehouseRepository {

    private final ShopWarehouseJpaRepository shopWarehouseJpaRepository;

    @Override
    @Transactional
    public ShopWarehouse save(ShopWarehouse shopWarehouse) {
        ShopWarehouseJpaEntity entity = shopWarehouseJpaRepository
                .findByTenantIdAndPlatformAndPlatformWarehouseId(
                        shopWarehouse.getTenantId(), shopWarehouse.getPlatform(), shopWarehouse.getPlatformWarehouseId())
                .orElseGet(ShopWarehouseJpaEntity::new);
        entity.setId(shopWarehouse.getId());
        entity.setTenantId(shopWarehouse.getTenantId());
        entity.setPlatform(shopWarehouse.getPlatform());
        entity.setIntegrationId(shopWarehouse.getIntegrationId());
        entity.setShopCode(shopWarehouse.getShopCode());
        entity.setShopName(shopWarehouse.getShopName());
        entity.setPlatformWarehouseId(shopWarehouse.getPlatformWarehouseId());
        entity.setPlatformWarehouseName(shopWarehouse.getPlatformWarehouseName());
        entity.setLocalWarehouseId(shopWarehouse.getLocalWarehouseId());
        entity.setLocalWarehouseCode(shopWarehouse.getLocalWarehouseCode());
        entity.setAddress(shopWarehouse.getAddress());
        entity.setDefaultWarehouse(shopWarehouse.isDefaultWarehouse());
        entity.setActive(shopWarehouse.isActive());
        entity.setLastSyncedAt(shopWarehouse.getLastSyncedAt());
        entity.setCreatedAt(shopWarehouse.getCreatedAt());
        entity.setUpdatedAt(shopWarehouse.getUpdatedAt());
        shopWarehouseJpaRepository.save(entity);
        return shopWarehouse;
    }

    @Override
    public Optional<ShopWarehouse> findById(String id) {
        return shopWarehouseJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ShopWarehouse> findByTenantIdAndPlatformAndPlatformWarehouseId(
            String tenantId, String platform, String platformWarehouseId) {
        return shopWarehouseJpaRepository
                .findByTenantIdAndPlatformAndPlatformWarehouseId(tenantId, platform, platformWarehouseId)
                .map(this::toDomain);
    }

    @Override
    public List<ShopWarehouse> findByTenantId(String tenantId) {
        return shopWarehouseJpaRepository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ShopWarehouse> findByTenantIdAndPlatform(String tenantId, String platform) {
        return shopWarehouseJpaRepository.findByTenantIdAndPlatform(tenantId, platform)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ShopWarehouse> findByLocalWarehouseId(String localWarehouseId) {
        return shopWarehouseJpaRepository.findByLocalWarehouseId(localWarehouseId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(String id) {
        shopWarehouseJpaRepository.deleteById(id);
    }

    private ShopWarehouse toDomain(ShopWarehouseJpaEntity entity) {
        return ShopWarehouse.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getShopCode(), entity.getShopName(),
                entity.getPlatformWarehouseId(), entity.getPlatformWarehouseName(),
                entity.getLocalWarehouseId(), entity.getLocalWarehouseCode(), entity.getAddress(),
                entity.isDefaultWarehouse(), entity.isActive(), entity.getLastSyncedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
