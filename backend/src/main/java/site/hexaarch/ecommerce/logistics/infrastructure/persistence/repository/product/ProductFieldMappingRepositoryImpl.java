package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductFieldMappingJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product.ProductFieldMappingJpaRepository;

/**
 * 第三方平台字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ProductFieldMappingRepositoryImpl implements ProductFieldMappingRepository {

    private final ProductFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<ProductFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<ProductFieldMapping> mappings = new ArrayList<>();
        for (ProductFieldMappingJpaEntity entity : fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId, platform)) {
            mappings.add(toDomain(entity));
        }
        return mappings;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return;
        }
        fieldMappingJpaRepository.deleteByTenantIdAndPlatform(tenantId, platform);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<ProductFieldMapping> saveAll(List<ProductFieldMapping> mappings) {
        List<ProductFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (ProductFieldMapping mapping : mappings) {
            ProductFieldMappingJpaEntity entity = new ProductFieldMappingJpaEntity();
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
        List<ProductFieldMapping> saved = new ArrayList<>(entities.size());
        for (ProductFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private ProductFieldMapping toDomain(ProductFieldMappingJpaEntity entity) {
        ProductFieldMapping mapping = new ProductFieldMapping();
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
