package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.service.tenant.CustomUserDetailsService;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.RoleJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.UserJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.UserMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.RoleJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.UserJpaRepository;

/**
 * UserRepository 实现类，用于操作用户实体的持久化操作。
 * <p>
 * 加载时会根据 user_roles 关联表把角色回填到领域用户上，
 * 保证认证（CustomUserDetailsService）与角色分配接口能取到完整角色数据。
 * </p>
 *
 * @author kenyon
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserMapper userMapper;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository,
                              RoleJpaRepository roleJpaRepository,
                              UserMapper userMapper) {
        this.userJpaRepository = userJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public User save(User user) {
        var jpaEntity = userMapper.toJpaEntity(user);
        var savedEntity = userJpaRepository.save(jpaEntity);
        return toDomainWithRoles(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        return userJpaRepository.findById(id).map(this::toDomainWithRoles);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsername(username).map(this::toDomainWithRoles);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(this::toDomainWithRoles);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByTenantId(String tenantId) {
        return userJpaRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDomainWithRoles)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        userJpaRepository.deleteById(id);
    }

    /**
     * 转换为领域用户并按角色ID回填角色集合
     */
    private User toDomainWithRoles(UserJpaEntity jpaEntity) {
        User user = userMapper.toDomainEntity(jpaEntity);
        Set<String> roleIds = jpaEntity.getRoleIds();
        if (user != null && roleIds != null && !roleIds.isEmpty()) {
            Map<String, RoleJpaEntity> roleMap = roleJpaRepository.findAllById(roleIds)
                    .stream()
                    .collect(Collectors.toMap(RoleJpaEntity::getId, Function.identity()));
            for (String roleId : roleIds) {
                RoleJpaEntity roleJpa = roleMap.get(roleId);
                if (roleJpa != null) {
                    user.addRole(toDomainRole(roleJpa));
                }
            }
        }
        return user;
    }

    private site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role toDomainRole(RoleJpaEntity roleJpa) {
        var role = site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role.create(
                roleJpa.getTenantId(), roleJpa.getName(), roleJpa.getDescription());
        role.setId(roleJpa.getId());
        role.setSystemRole(roleJpa.isSystemRole());
        role.setPermissions(roleJpa.getPermissions() == null ? new java.util.HashSet<>() : new java.util.HashSet<>(roleJpa.getPermissions()));
        role.setUpdatedAt(roleJpa.getUpdatedAt());
        return role;
    }
}
