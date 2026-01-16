package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.PlatformStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.PlatformIntegrationMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.PlatformIntegrationJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 平台集成存储库实现类，用于在数据库中执行平台集成聚合根的CRUD操作。
 *
 * @author kenyon
 */
@Repository
public class PlatformIntegrationRepositoryImpl implements PlatformIntegrationRepository {

    private final PlatformIntegrationJpaRepository jpaRepository;
    private final PlatformIntegrationMapper mapper;

    @Autowired
    public PlatformIntegrationRepositoryImpl(PlatformIntegrationJpaRepository jpaRepository, PlatformIntegrationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PlatformIntegration> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public PlatformIntegration save(PlatformIntegration platformIntegration) {
        PlatformIntegrationJpaEntity jpaEntity = mapper.toJpaEntity(platformIntegration);
        PlatformIntegrationJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<PlatformIntegration> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlatformIntegration> findByStatusActive() {
        return jpaRepository.findByStatus(PlatformStatusJpaEntity.ACTIVE).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
