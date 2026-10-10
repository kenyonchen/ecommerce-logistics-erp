package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.platform;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.platform.PlatformIntegrationMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.platform.PlatformIntegrationJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.CredentialCipher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 平台集成存储库实现类，用于在数据库中执行平台集成聚合根的CRUD操作。
 * <p>
 * 凭据（apiKey/apiSecret/accessToken/refreshToken）在写库前加密、读库后解密，
 * 加解密收敛在仓储边界，领域聚合与上层只看到明文，避免密文漏进业务逻辑与 API 出参。
 *
 * @author kenyon
 */
@Repository
public class PlatformIntegrationRepositoryImpl implements PlatformIntegrationRepository {

    private final PlatformIntegrationJpaRepository jpaRepository;
    private final PlatformIntegrationMapper mapper;
    private final CredentialCipher credentialCipher;

    @Autowired
    public PlatformIntegrationRepositoryImpl(PlatformIntegrationJpaRepository jpaRepository,
                                            PlatformIntegrationMapper mapper,
                                            CredentialCipher credentialCipher) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public Optional<PlatformIntegration> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<PlatformIntegration> findByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return jpaRepository.findByTenantId(tenantId.trim()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PlatformIntegration save(PlatformIntegration platformIntegration) {
        PlatformIntegrationJpaEntity jpaEntity = mapper.toJpaEntity(platformIntegration);
        encryptSecrets(jpaEntity);
        // 主键是聚合 create() 预分配的 UUID，库里没有该行即新记录——不声明的话 Spring Data 只看 id
        // 会走 merge() 去更新一条不存在的行，新建集成恒 500（unsaved-value）
        jpaEntity.setNewRecord(jpaEntity.getId() == null || !jpaRepository.existsById(jpaEntity.getId()));
        PlatformIntegrationJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        PlatformIntegration savedIntegration = toDomain(savedEntity);
        // save 重建的聚合副本不携带领域事件，补回后应用服务才能发布（ISS-062）
        BaseAggregateRoot.carryOverEvents(platformIntegration, savedIntegration);
        return savedIntegration;
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<PlatformIntegration> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlatformIntegration> findByStatusActive() {
        return jpaRepository.findByStatus(PlatformStatusJpaEntity.ACTIVE).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private PlatformIntegration toDomain(PlatformIntegrationJpaEntity jpaEntity) {
        // 实体可能仍处于托管态，就地解密会被 dirty-check 当成变更，flush 时把明文凭据覆盖回密文列（ISS-011）
        PlatformIntegrationJpaEntity snapshot = mapper.copy(jpaEntity);
        decryptSecrets(snapshot);
        return mapper.toDomain(snapshot);
    }

    private void encryptSecrets(PlatformIntegrationJpaEntity entity) {
        entity.setApiKey(credentialCipher.encrypt(entity.getApiKey()));
        entity.setApiSecret(credentialCipher.encrypt(entity.getApiSecret()));
        entity.setAccessToken(credentialCipher.encrypt(entity.getAccessToken()));
        entity.setRefreshToken(credentialCipher.encrypt(entity.getRefreshToken()));
    }

    private void decryptSecrets(PlatformIntegrationJpaEntity entity) {
        entity.setApiKey(credentialCipher.decrypt(entity.getApiKey()));
        entity.setApiSecret(credentialCipher.decrypt(entity.getApiSecret()));
        entity.setAccessToken(credentialCipher.decrypt(entity.getAccessToken()));
        entity.setRefreshToken(credentialCipher.decrypt(entity.getRefreshToken()));
    }
}
