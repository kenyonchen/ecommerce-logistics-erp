package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinanceFieldMappingJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinanceFieldMappingJpaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方平台财务字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class FinanceFieldMappingRepositoryImpl implements FinanceFieldMappingRepository {

    private final FinanceFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<FinanceFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<FinanceFieldMapping> mappings = new ArrayList<>();
        for (FinanceFieldMappingJpaEntity entity : fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId, platform)) {
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
    public List<FinanceFieldMapping> saveAll(List<FinanceFieldMapping> mappings) {
        List<FinanceFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (FinanceFieldMapping mapping : mappings) {
            FinanceFieldMappingJpaEntity entity = new FinanceFieldMappingJpaEntity();
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
        List<FinanceFieldMapping> saved = new ArrayList<>(entities.size());
        for (FinanceFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private FinanceFieldMapping toDomain(FinanceFieldMappingJpaEntity entity) {
        FinanceFieldMapping mapping = new FinanceFieldMapping();
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
