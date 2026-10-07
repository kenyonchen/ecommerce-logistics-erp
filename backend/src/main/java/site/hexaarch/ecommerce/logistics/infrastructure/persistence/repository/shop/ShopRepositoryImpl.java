package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.shop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.shop.aggregate.Shop;
import site.hexaarch.ecommerce.logistics.domain.shop.repository.ShopRepository;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.shop.ShopJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.shop.ShopJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.platform.PlatformIntegrationRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.security.CredentialCipher;

import java.util.List;
import java.util.Optional;

/**
 * 店铺仓储实现。
 * <p>
 * 三条纪律：
 * <ol>
 *   <li><b>每一句查询都带 {@code tenantId}</b>。基类自带的 {@code findById}/{@code findAll} 在此一律不调用，
 *       这是 ADR-0013 §4.3 锁 2 的落点；更新已有店铺时用 {@code findByIdAndTenantId} 而不是裸 id。</li>
 *   <li><b>加解密收敛在本类边界</b>：聚合内是明文，库里是 {@code enc:v1:} 密文。
 *       写入前先加密、读取时不改动受管实体（解密结果直接进 {@link Shop.restore}），
 *       避免 Hibernate 脏检查把解密后的明文回填到库——这是 {@code PlatformIntegrationRepositoryImpl}
 *       现有 {@code decryptSecrets(entity)} 就地改写模式的隐患，店铺域不复制。</li>
 *   <li><b>枚举↔字符串在此转换</b>：库列是 varchar（{@code ddl-auto=update} 不建 ENUM），聚合里是枚举。</li>
 * </ol>
 *
 * @author kenyon
 */
@Slf4j
@Repository
public class ShopRepositoryImpl implements ShopRepository {

    private final ShopJpaRepository jpaRepository;
    private final CredentialCipher credentialCipher;

    @Autowired
    public ShopRepositoryImpl(ShopJpaRepository jpaRepository, CredentialCipher credentialCipher) {
        this.jpaRepository = jpaRepository;
        this.credentialCipher = credentialCipher;
    }

    /**
     * 保存。用新建的游离实体做 merge，使「明文中转」只发生在游离对象上，永不污染受管实例。
     * <p>
     * 返回入参聚合本身而不是从库记录重建：{@code Shop.restore} 不注册事件，重建会把
     * {@code ShopCreatedEvent}、授权状态变更事件丢掉，上层 {@code publishEventsFrom(shop)} 就什么也发不出去。
     * </p>
     */
    @Override
    public Shop save(Shop shop) {
        ShopJpaEntity entity = new ShopJpaEntity();
        toEntity(shop, entity);
        encryptSecrets(entity);
        jpaRepository.save(entity);
        return shop;
    }

    @Override
    public Optional<Shop> findByIdAndTenantId(String id, String tenantId) {
        if (isBlank(id) || isBlank(tenantId)) {
            return Optional.empty();
        }
        return jpaRepository.findByIdAndTenantId(id.trim(), tenantId.trim()).map(this::toDomain);
    }

    @Override
    public List<Shop> findByTenantId(String tenantId) {
        if (isBlank(tenantId)) {
            return List.of();
        }
        return jpaRepository.findByTenantIdOrderByShopCodeAsc(tenantId.trim()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Shop> findByTenantIdAndPlatform(String tenantId, String platform) {
        if (isBlank(tenantId) || isBlank(platform)) {
            return List.of();
        }
        return jpaRepository.findByTenantIdAndPlatform(tenantId.trim(), platform.trim().toUpperCase()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Shop> findByTenantIdAndShopCode(String tenantId, String shopCode) {
        if (isBlank(tenantId) || isBlank(shopCode)) {
            return Optional.empty();
        }
        return jpaRepository.findByTenantIdAndShopCode(tenantId.trim(), shopCode.trim()).map(this::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndShopCode(String tenantId, String shopCode, String excludeId) {
        if (isBlank(tenantId) || isBlank(shopCode)) {
            return false;
        }
        if (isBlank(excludeId)) {
            return jpaRepository.existsByTenantIdAndShopCode(tenantId.trim(), shopCode.trim());
        }
        return jpaRepository.findByTenantIdAndShopCode(tenantId.trim(), shopCode.trim())
                .filter(entity -> !excludeId.trim().equals(entity.getId()))
                .isPresent();
    }

    /**
     * 站点预检。无站点概念的平台 {@code marketplaceId} 恒为空，直接返回 false——
     * 它们靠 {@code uk_shops_tenant_code} 约束，不参与站点唯一性判断。
     */
    @Override
    public boolean existsByTenantIdAndPlatformAndMarketplaceId(String tenantId, String platform, String marketplaceId) {
        if (isBlank(tenantId) || isBlank(platform) || isBlank(marketplaceId)) {
            return false;
        }
        return jpaRepository.existsByTenantIdAndPlatformAndMarketplaceId(
                tenantId.trim(), platform.trim().toUpperCase(), marketplaceId.trim());
    }

    @Override
    public long countByTenantId(String tenantId) {
        if (isBlank(tenantId)) {
            return 0L;
        }
        return jpaRepository.countByTenantId(tenantId.trim());
    }

    @Override
    public void deleteByIdAndTenantId(String id, String tenantId) {
        if (isBlank(id) || isBlank(tenantId)) {
            return;
        }
        jpaRepository.findByIdAndTenantId(id.trim(), tenantId.trim())
                .ifPresent(jpaRepository::delete);
    }

    private Shop toDomain(ShopJpaEntity entity) {
        return Shop.restore(entity.getId(), entity.getTenantId(), entity.getIntegrationId(),
                entity.getPlatform(), entity.getShopCode(), entity.getShopName(),
                entity.getMarketplaceId(), entity.getRegion(), entity.getExternalShopId(),
                entity.getStoreUrl(), credentialCipher.decrypt(entity.getApiKey()),
                credentialCipher.decrypt(entity.getApiSecret()),
                credentialCipher.decrypt(entity.getAccessToken()),
                credentialCipher.decrypt(entity.getRefreshToken()), entity.getTokenExpiresAt(),
                entity.getApiVersion(), parseAuthStatus(entity.getAuthStatus()),
                parsePushBackPolicy(entity.getPushBackPolicy()), entity.isActive(),
                entity.getLastOrdersSyncedAt(), entity.getLastProductsSyncedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void toEntity(Shop shop, ShopJpaEntity entity) {
        entity.setId(shop.getId());
        entity.setTenantId(shop.getTenantId());
        entity.setIntegrationId(shop.getIntegrationId());
        entity.setPlatform(shop.getPlatform());
        entity.setShopCode(shop.getShopCode());
        entity.setShopName(shop.getShopName());
        entity.setMarketplaceId(shop.getMarketplaceId());
        entity.setRegion(shop.getRegion());
        entity.setExternalShopId(shop.getExternalShopId());
        entity.setStoreUrl(shop.getStoreUrl());
        entity.setApiKey(shop.getApiKey());
        entity.setApiSecret(shop.getApiSecret());
        entity.setAccessToken(shop.getAccessToken());
        entity.setRefreshToken(shop.getRefreshToken());
        entity.setTokenExpiresAt(shop.getTokenExpiresAt());
        entity.setApiVersion(shop.getApiVersion());
        entity.setAuthStatus(shop.getAuthStatus() == null
                ? ShopAuthStatus.PENDING.name() : shop.getAuthStatus().name());
        entity.setPushBackPolicy(shop.getPushBackPolicy() == null
                ? PushBackPolicy.MANUAL.name() : shop.getPushBackPolicy().name());
        entity.setActive(shop.isActive());
        entity.setLastOrdersSyncedAt(shop.getLastOrdersSyncedAt());
        entity.setLastProductsSyncedAt(shop.getLastProductsSyncedAt());
        entity.setCreatedAt(shop.getCreatedAt());
        entity.setUpdatedAt(shop.getUpdatedAt());
    }

    private void encryptSecrets(ShopJpaEntity entity) {
        entity.setApiKey(credentialCipher.encrypt(entity.getApiKey()));
        entity.setApiSecret(credentialCipher.encrypt(entity.getApiSecret()));
        entity.setAccessToken(credentialCipher.encrypt(entity.getAccessToken()));
        entity.setRefreshToken(credentialCipher.encrypt(entity.getRefreshToken()));
    }

    private static ShopAuthStatus parseAuthStatus(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ShopAuthStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[ShopRepository] 未知的店铺授权状态值 {}，按 PENDING 处理", raw);
            return null;
        }
    }

    private static PushBackPolicy parsePushBackPolicy(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PushBackPolicy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[ShopRepository] 未知的发货回写策略值 {}，按 MANUAL 处理", raw);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
