package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseFieldMappingJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.ShopWarehouseFieldMappingJpaRepository;

/**
 * 店铺仓库字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ShopWarehouseFieldMappingRepositoryImpl implements ShopWarehouseFieldMappingRepository {

    private final ShopWarehouseFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<ShopWarehouseFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<ShopWarehouseFieldMapping> mappings = new ArrayList<>();
        for (ShopWarehouseFieldMappingJpaEntity entity :
                fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId, platform)) {
            mappings.add(toDomain(entity));
        }
        return mappings;
    }

    @Override
    @Transactional
    public void deleteByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return;
        }
        fieldMappingJpaRepository.deleteByTenantIdAndPlatform(tenantId, platform);
    }

    @Override
    @Transactional
    public List<ShopWarehouseFieldMapping> saveAll(List<ShopWarehouseFieldMapping> mappings) {
        List<ShopWarehouseFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (ShopWarehouseFieldMapping mapping : mappings) {
            ShopWarehouseFieldMappingJpaEntity entity = new ShopWarehouseFieldMappingJpaEntity();
            entity.setId(mapping.getId());
            entity.setTenantId(mapping.getTenantId());
            entity.setPlatform(mapping.getPlatform());
            entity.setSourceField(mapping.getSourceField());
            entity.setTargetField(mapping.getTargetField());
            entity.setTransform(mapping.getTransform());
            entity.setDefaultValue(mapping.getDefaultValue());
            entity.setEnabled(mapping.isEnabled());
            entity.setCreatedAt(mapping.getCreatedAt());
            entities.add(entity);
        }
        List<ShopWarehouseFieldMapping> saved = new ArrayList<>(entities.size());
        for (ShopWarehouseFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private ShopWarehouseFieldMapping toDomain(ShopWarehouseFieldMappingJpaEntity entity) {
        ShopWarehouseFieldMapping mapping = new ShopWarehouseFieldMapping();
        mapping.setId(entity.getId());
        mapping.setTenantId(entity.getTenantId());
        mapping.setPlatform(entity.getPlatform());
        mapping.setSourceField(entity.getSourceField());
        mapping.setTargetField(entity.getTargetField());
        mapping.setTransform(entity.getTransform());
        mapping.setDefaultValue(entity.getDefaultValue());
        mapping.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        mapping.setCreatedAt(entity.getCreatedAt());
        return mapping;
    }
}
