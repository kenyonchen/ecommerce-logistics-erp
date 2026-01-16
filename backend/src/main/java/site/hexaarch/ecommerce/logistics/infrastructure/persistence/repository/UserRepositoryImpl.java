package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.UserMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.UserJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * UserRepository 实现类，用于操作用户实体的持久化操作。
 *
 * @author kenyon
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository, UserMapper userMapper) {
        this.userJpaRepository = userJpaRepository;
        this.userMapper = userMapper;
    }

    @Override
    public User save(User user) {
        var jpaEntity = userMapper.toJpaEntity(user);
        var savedEntity = userJpaRepository.save(jpaEntity);
        return userMapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<User> findById(String id) {
        return userJpaRepository.findById(id)
                .map(userMapper::toDomainEntity);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsername(username)
                .map(userMapper::toDomainEntity);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(userMapper::toDomainEntity);
    }

    @Override
    public List<User> findByTenantId(String tenantId) {
        return userJpaRepository.findByTenantId(tenantId)
                .stream()
                .map(userMapper::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        userJpaRepository.deleteById(id);
    }
}
