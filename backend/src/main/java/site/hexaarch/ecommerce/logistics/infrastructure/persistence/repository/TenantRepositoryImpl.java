package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.TenantMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.TenantJpaRepository;

import java.util.Optional;

/**
 * TenantRepository 实现类，用于操作租户聚合根的持久化操作。
 *
 * @author kenyon
 */
@Repository
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantJpaRepository tenantJpaRepository;
    private final TenantMapper tenantMapper;

    public TenantRepositoryImpl(TenantJpaRepository tenantJpaRepository, TenantMapper tenantMapper) {
        this.tenantJpaRepository = tenantJpaRepository;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public Tenant save(Tenant tenant) {
        var jpaEntity = tenantMapper.toJpaEntity(tenant);
        var savedEntity = tenantJpaRepository.save(jpaEntity);
        return tenantMapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return tenantJpaRepository.findById(id)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public Optional<Tenant> findByName(String name) {
        return tenantJpaRepository.findByName(name)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public Optional<Tenant> findByContactEmail(String email) {
        return tenantJpaRepository.findByContactEmail(email)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public void deleteById(String id) {
        tenantJpaRepository.deleteById(id);
    }
}
