package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerFieldMappingJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer.CustomerFieldMappingJpaRepository;

/**
 * 第三方平台客户字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class CustomerFieldMappingRepositoryImpl implements CustomerFieldMappingRepository {

    private final CustomerFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<CustomerFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<CustomerFieldMapping> mappings = new ArrayList<>();
        for (CustomerFieldMappingJpaEntity entity : fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId, platform)) {
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
    public List<CustomerFieldMapping> saveAll(List<CustomerFieldMapping> mappings) {
        List<CustomerFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (CustomerFieldMapping mapping : mappings) {
            CustomerFieldMappingJpaEntity entity = new CustomerFieldMappingJpaEntity();
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
        List<CustomerFieldMapping> saved = new ArrayList<>(entities.size());
        for (CustomerFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private CustomerFieldMapping toDomain(CustomerFieldMappingJpaEntity entity) {
        CustomerFieldMapping mapping = new CustomerFieldMapping();
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
