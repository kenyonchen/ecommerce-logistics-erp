package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.RoleMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.RoleJpaRepository;

/**
 * RoleRepository 实现类，用于操作角色实体的持久化操作。
 *
 * @author kenyon
 */
@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final RoleMapper roleMapper;

    public RoleRepositoryImpl(RoleJpaRepository roleJpaRepository, RoleMapper roleMapper) {
        this.roleJpaRepository = roleJpaRepository;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional
    public Role save(Role role) {
        var jpaEntity = roleMapper.toJpaEntity(role);
        var savedEntity = roleJpaRepository.save(jpaEntity);
        return roleMapper.toDomainEntity(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(String id) {
        return roleJpaRepository.findById(id)
                .map(roleMapper::toDomainEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByNameAndTenantId(String name, String tenantId) {
        return roleJpaRepository.findByNameAndTenantId(name, tenantId)
                .map(roleMapper::toDomainEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findByTenantId(String tenantId) {
        return roleJpaRepository.findByTenantId(tenantId)
                .stream()
                .map(roleMapper::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        roleJpaRepository.deleteById(id);
    }
}
