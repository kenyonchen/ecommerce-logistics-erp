package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderFieldMappingJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderFieldMappingJpaRepository;

/**
 * 第三方平台订单字段映射仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class OrderFieldMappingRepositoryImpl implements OrderFieldMappingRepository {

    private final OrderFieldMappingJpaRepository fieldMappingJpaRepository;

    @Override
    public List<OrderFieldMapping> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (tenantId == null || platform == null) {
            return List.of();
        }
        List<OrderFieldMapping> mappings = new ArrayList<>();
        for (OrderFieldMappingJpaEntity entity : fieldMappingJpaRepository.findByTenantIdAndPlatform(tenantId, platform)) {
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
    public List<OrderFieldMapping> saveAll(List<OrderFieldMapping> mappings) {
        List<OrderFieldMappingJpaEntity> entities = new ArrayList<>(mappings.size());
        for (OrderFieldMapping mapping : mappings) {
            OrderFieldMappingJpaEntity entity = new OrderFieldMappingJpaEntity();
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
        List<OrderFieldMapping> saved = new ArrayList<>(entities.size());
        for (OrderFieldMappingJpaEntity entity : fieldMappingJpaRepository.saveAll(entities)) {
            saved.add(toDomain(entity));
        }
        return saved;
    }

    private OrderFieldMapping toDomain(OrderFieldMappingJpaEntity entity) {
        OrderFieldMapping mapping = new OrderFieldMapping();
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
