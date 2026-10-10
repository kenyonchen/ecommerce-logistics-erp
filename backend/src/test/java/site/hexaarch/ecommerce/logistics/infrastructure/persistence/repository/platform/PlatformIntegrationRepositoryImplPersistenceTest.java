package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformAuthTypeJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.platform.PlatformIntegrationMapperImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.platform.PlatformIntegrationJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.CredentialCipher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台集成仓储落库回归测试（{@code POST /api/platform-integrations} 报 500 unsaved-value）。
 * <p>
 * {@code PlatformIntegration.create()} 在入库前就给聚合分配了 UUID，Spring Data 的
 * {@code SimpleJpaRepository.save()} 只看 id 判定游离性，于是走 {@code merge()}：SELECT 落空后
 * 对不存在的行发 UPDATE，抛 {@code StaleObjectStateException}，新集成永远插不进去。
 * 这个分支只有真实的 JPA 会话才测得出来——纯 Mockito 桩里 {@code save()} 返回什么由测试自己决定，
 * 因此本类用 H2 内存库 + 真实 {@code SimpleJpaRepository} 跑完整条 save 路径，
 * 提交事务后直接查库断言行真的被 INSERT。
 *
 * @author kenyon
 */
class PlatformIntegrationRepositoryImplPersistenceTest {

    private static final String MASTER_KEY = "platform-integration-persistence-test-key";

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private PlatformIntegrationRepositoryImpl repository;

    @BeforeAll
    static void buildEntityManagerFactory() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url",
                        "jdbc:h2:mem:platform_integration_repository;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .build();
        entityManagerFactory = new MetadataSources(registry)
                .addAnnotatedClass(PlatformIntegrationJpaEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        repository = new PlatformIntegrationRepositoryImpl(jpaRepository(), new PlatformIntegrationMapperImpl(),
                new CredentialCipher(MASTER_KEY));
        inTransaction(() -> entityManager.createNativeQuery("delete from platform_integrations").executeUpdate());
    }

    @AfterEach
    void tearDown() {
        if (entityManager.isOpen()) {
            entityManager.close();
        }
    }

    @Test
    void shouldInsertRowWhenAggregateIsNew() {
        PlatformIntegration integration = customShopIntegration();
        integration.create();
        UUID domainId = integration.getId();

        PlatformIntegration saved = inTransaction(() -> repository.save(integration));

        // 主键必须是聚合预分配的那个，否则 create() 注册的 PlatformIntegrationCreatedEvent 指向不存在的行
        assertEquals(domainId, saved.getId());
        assertEquals("ak-test", saved.getApiKey(), "save 返回的聚合应拿到解密后的凭据");

        List<PlatformIntegrationJpaEntity> rows = readRows();
        assertEquals(1, rows.size(), "新建集成必须 INSERT 一行，而不是对不存在的行发 UPDATE");
        PlatformIntegrationJpaEntity row = rows.get(0);
        assertEquals(domainId, row.getId());
        assertEquals(PlatformStatusJpaEntity.INACTIVE, row.getStatus());
        assertEquals("https://api.ozon.example", row.getStoreUrl());
        // ISS-011：密文落库，明文只出现在领域侧
        assertTrue(CredentialCipher.isEncrypted(row.getApiKey()), "apiKey 必须以 enc:v1: 密文落库");
        assertNotEquals("ak-test", row.getApiKey());
        assertTrue(CredentialCipher.isEncrypted(row.getApiSecret()), "apiSecret 必须以 enc:v1: 密文落库");

        // GET /api/platform-integrations/{id} 的读路径：能查到且解密回明文
        PlatformIntegration reloaded = inTransaction(() -> repository.findById(domainId).orElseThrow());
        assertEquals("ak-test", reloaded.getApiKey());
        assertEquals("sk-test", reloaded.getApiSecret());
        assertEquals(PlatformStatus.INACTIVE, reloaded.getStatus());
    }

    @Test
    void shouldUpdateExistingRowWhenAggregateAlreadyPersisted() {
        PlatformIntegration integration = customShopIntegration();
        integration.create();
        UUID id = integration.getId();
        inTransaction(() -> repository.save(integration));

        PlatformIntegration loaded = inTransaction(() -> repository.findById(id).orElseThrow());
        loaded.activate();
        loaded.applyToken("rotated-access-token", null, null);
        inTransaction(() -> repository.save(loaded));

        List<PlatformIntegrationJpaEntity> rows = readRows();
        assertEquals(1, rows.size(), "已存在的集成再次 save 只能 UPDATE 原行，不能插出第二行");
        assertEquals(PlatformStatusJpaEntity.ACTIVE, rows.get(0).getStatus());
        assertTrue(CredentialCipher.isEncrypted(rows.get(0).getAccessToken()), "更新的凭据同样要密文落库");
        PlatformIntegration reloaded = inTransaction(() -> repository.findById(id).orElseThrow());
        assertEquals(PlatformStatus.ACTIVE, reloaded.getStatus());
        assertEquals("rotated-access-token", reloaded.getAccessToken());
    }

    @Test
    void shouldInsertShopifyAccessTokenEncrypted() {
        // apiKey/apiSecret 两列 NOT NULL：只认访问令牌的协议由领域服务归一化成空串占位
        PlatformIntegration integration = PlatformIntegration.builder()
                .tenantId("default-tenant")
                .platformName(PlatformName.SHOPIFY)
                .authType(PlatformAuthType.SHOPIFY)
                .storeUrl("https://shop.myshopify.com")
                .apiKey("")
                .apiSecret("")
                .accessToken("shpat_test_token")
                .build();
        integration.create();

        inTransaction(() -> repository.save(integration));

        List<PlatformIntegrationJpaEntity> rows = readRows();
        assertEquals(1, rows.size());
        assertEquals(PlatformAuthTypeJpaEntity.SHOPIFY, rows.get(0).getAuthType());
        assertTrue(CredentialCipher.isEncrypted(rows.get(0).getAccessToken()), "accessToken 必须以密文落库");
        assertEquals("shpat_test_token",
                inTransaction(() -> repository.findById(integration.getId()).orElseThrow()).getAccessToken());
    }

    private PlatformIntegration customShopIntegration() {
        return PlatformIntegration.builder()
                .tenantId("default-tenant")
                .platformName(PlatformName.OZON)
                .authType(PlatformAuthType.CUSTOM_SHOP)
                .storeUrl("https://api.ozon.example")
                .apiKey("ak-test")
                .apiSecret("sk-test")
                .build();
    }

    /**
     * 用真实 {@code SimpleJpaRepository} 接住 {@link PlatformIntegrationJpaRepository}：
     * persist/merge 的判定就发生在这里，桩件换不来。
     */
    private PlatformIntegrationJpaRepository jpaRepository() {
        SimpleJpaRepository<PlatformIntegrationJpaEntity, UUID> delegate =
                new SimpleJpaRepository<>(PlatformIntegrationJpaEntity.class, entityManager);
        return (PlatformIntegrationJpaRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PlatformIntegrationJpaRepository.class},
                (proxy, method, args) -> {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        // 让 JPA 异常按原样冒泡，否则代理会包成 UndeclaredThrowableException
                        throw e.getTargetException();
                    }
                });
    }

    private <T> T inTransaction(Supplier<T> action) {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        try {
            T result = action.get();
            transaction.commit();
            return result;
        } catch (RuntimeException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        }
    }

    private void inTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 提交后清掉一级缓存再查，读到的必须是库里的行。
     */
    private List<PlatformIntegrationJpaEntity> readRows() {
        return inTransaction(() -> {
            entityManager.clear();
            return entityManager.createQuery("select e from PlatformIntegrationJpaEntity e",
                    PlatformIntegrationJpaEntity.class).getResultList();
        });
    }
}
