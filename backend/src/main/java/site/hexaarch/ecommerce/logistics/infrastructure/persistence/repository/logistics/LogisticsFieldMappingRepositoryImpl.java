package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.logistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsFieldMappingJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics.LogisticsFieldMappingJpaRepository;

/**
 * 物流字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class LogisticsFieldMappingRepositoryImpl implements LogisticsFieldMappingRepository {

    private final LogisticsFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<LogisticsFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<LogisticsFieldMapping> mappings = new ArrayList<>();
        for (LogisticsFieldMappingJpaEntity entity : fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId,
                platform)) {
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
    public List<LogisticsFieldMapping> saveAll(List<LogisticsFieldMapping> mappings) {
        List<LogisticsFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (LogisticsFieldMapping mapping : mappings) {
            LogisticsFieldMappingJpaEntity entity = new LogisticsFieldMappingJpaEntity();
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
        List<LogisticsFieldMapping> saved = new ArrayList<>(entities.size());
        for (LogisticsFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private LogisticsFieldMapping toDomain(LogisticsFieldMappingJpaEntity entity) {
        LogisticsFieldMapping mapping = new LogisticsFieldMapping();
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
