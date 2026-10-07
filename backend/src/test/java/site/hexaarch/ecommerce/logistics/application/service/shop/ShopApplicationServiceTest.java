package site.hexaarch.ecommerce.logistics.application.service.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import site.hexaarch.ecommerce.logistics.application.command.shop.CreateShopCommand;
import site.hexaarch.ecommerce.logistics.application.command.shop.ShopCredentialCommand;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.shop.aggregate.Shop;
import site.hexaarch.ecommerce.logistics.domain.shop.repository.ShopRepository;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.shop.ShopRepositoryImpl;

/**
 * 店铺应用服务单元测试，覆盖 ADR-0013 §4.3 的两条隔离锁与 §4.1 的凭据边界。
 * <p>
 * 断言刻意盯着「仓储被以什么参数调用」而不只是返回值：租户隔离的实现方式是
 * 「只调带租户条件的查询」，所以 mock 的默认回答是「他租户的数据查得到、本租户的查不到」，
 * 一旦有人把调用换成裸 {@code findById}，这些用例会红。
 * </p>
 *
 * @author kenyon
 */
class ShopApplicationServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String OTHER_TENANT = "tenant-b";
    private static final String SHOP_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID INTEGRATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ShopRepository shopRepository;
    private PlatformIntegrationRepository platformIntegrationRepository;
    private DomainEventPublisher domainEventPublisher;
    private ShopApplicationService service;

    @BeforeEach
    void setUp() {
        shopRepository = Mockito.mock(ShopRepository.class);
        platformIntegrationRepository = Mockito.mock(PlatformIntegrationRepository.class);
        domainEventPublisher = Mockito.mock(DomainEventPublisher.class);
        service = new ShopApplicationService(shopRepository, platformIntegrationRepository, domainEventPublisher);
    }

    /**
     * 属于本租户的店铺。
     */
    private Shop ownShop(String platform, String marketplaceId, String region) {
        return Shop.restore(SHOP_ID, TENANT, null, platform, "QA-01", "探针店", marketplaceId, region,
                null, null, "key-1", null, null, null, null, null, ShopAuthStatus.ACTIVE, null,
                true, null, null, null, null);
    }

    @Test
    @DisplayName("读别的租户的店铺一律 404，且只走带租户条件的查询")
    void crossTenantReadIs404AndUsesTenantFilteredQuery() {
        // 他租户视角：裸 findById 查得到，本租户过滤查询查不到
        Shop foreign = Shop.restore(SHOP_ID, OTHER_TENANT, null, "AMAZON", "FOREIGN", "别家店", null, null,
                null, null, null, null, null, null, null, null, ShopAuthStatus.ACTIVE, null,
                true, null, null, null, null);
        when(shopRepository.findByIdAndTenantId(SHOP_ID, TENANT)).thenReturn(Optional.empty());
        when(shopRepository.findByIdAndTenantId(SHOP_ID, OTHER_TENANT)).thenReturn(Optional.of(foreign));

        EntityNotFoundException e = assertThrows(EntityNotFoundException.class,
                () -> service.getShop(TENANT, SHOP_ID));
        assertTrue(e.getMessage().contains(SHOP_ID), "404 信息应指向被请求的ID: " + e.getMessage());
        verify(shopRepository).findByIdAndTenantId(SHOP_ID, TENANT);
    }

    @Test
    @DisplayName("tenantId 为空的每个入口都被服务层自己挡下")
    void blankTenantIdIsRejectedAtEveryEntry() {
        CreateShopCommand command = new CreateShopCommand(null, "AMAZON", "QA-01", "店", null, null);
        assertThrows(ValidationException.class, () -> service.listShops(" ", null));
        assertThrows(ValidationException.class, () -> service.getShop("", SHOP_ID));
        assertThrows(ValidationException.class, () -> service.countShops(null));
        assertThrows(ValidationException.class, () -> service.findShopByCode("", "QA-01"));
        assertThrows(ValidationException.class, () -> service.createShop(null, command));
        assertThrows(ValidationException.class, () -> service.updateShop("", SHOP_ID, "改名", null));
        assertThrows(ValidationException.class,
                () -> service.changePushBackPolicy(null, SHOP_ID, null));
        assertThrows(ValidationException.class,
                () -> service.bindCredentials("", SHOP_ID, new ShopCredentialCommand("k", null, null, null,
                        null, null, null, null, null, null)));
        assertThrows(ValidationException.class, () -> service.deleteShop(null, SHOP_ID));
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("新建店铺：region 小写也归一成大写后落库")
    void createShopPersistsNormalizedRegion() {
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-01", null)).thenReturn(false);
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop saved = service.createShop(TENANT,
                new CreateShopCommand(null, "amazon", "QA-01", "探针店", "na", null));

        assertEquals("AMAZON", saved.getPlatform());
        assertEquals("NA", saved.getRegion());
        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).save(captor.capture());
        verify(domainEventPublisher).publishEventsFrom(saved);
        assertEquals("NA", captor.getValue().getRegion());
    }

    @Test
    @DisplayName("新建店铺：编码重复时不落库")
    void createShopRejectsDuplicateShopCode() {
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-01", null)).thenReturn(true);

        ValidationException e = assertThrows(ValidationException.class, () -> service.createShop(TENANT,
                new CreateShopCommand(null, "AMAZON", "QA-01", "探针店", "NA", null)));
        assertTrue(e.getMessage().contains("QA-01"), "错误信息要指向冲突的编码字段");
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("新建店铺：Amazon 区域只接受 NA/EU/FE")
    void createShopRejectsUnknownAmazonRegion() {
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-01", null)).thenReturn(false);

        assertThrows(ValidationException.class, () -> service.createShop(TENANT,
                new CreateShopCommand(null, "AMAZON", "QA-01", "探针店", "US", null)));
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("非 Amazon 平台的 region 是自由填写的站点标识，只归一大小写")
    void createShopAcceptsFreeFormRegionForNonAmazon() {
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-01", null)).thenReturn(false);
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop saved = service.createShop(TENANT,
                new CreateShopCommand(null, "OZON", "QA-01", "探针店", "russia", null));
        assertEquals("RUSSIA", saved.getRegion());
    }

    /**
     * 这条用例盯 {@code validateIntegration} 的实现选择：必须用带租户的 {@code findByTenantId}，
     * 不能用裸 {@code findById}。这里让「裸 findById 查得到、本租户过滤后查不到」，
     * 于是正确实现抛校验错误；一旦有人改回裸 {@code findById}，A 租户就能借用 B 租户的 app 凭据建店，
     * 这条用例的 {@code assertThrows} 会因创建成功而变红。
     */
    @Test
    @DisplayName("对接定义跨租户引用被拒绝：校验只认本租户的对接定义")
    void createShopRejectsIntegrationOfAnotherTenant() {
        PlatformIntegration foreign = Mockito.mock(PlatformIntegration.class);
        when(foreign.getId()).thenReturn(INTEGRATION_ID);
        // 反面例子路径：裸 findById 会成功
        when(platformIntegrationRepository.findById(INTEGRATION_ID)).thenReturn(Optional.of(foreign));
        // 正确路径：按租户过滤后看不到
        when(platformIntegrationRepository.findByTenantId(TENANT)).thenReturn(List.of());

        assertThrows(ValidationException.class, () -> service.createShop(TENANT,
                new CreateShopCommand(INTEGRATION_ID.toString(), "AMAZON", "QA-01", "探针店", "NA", null)));
        verify(platformIntegrationRepository).findByTenantId(TENANT);
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("对接定义属于本租户时放行")
    void createShopAcceptsOwnIntegration() {
        PlatformIntegration own = Mockito.mock(PlatformIntegration.class);
        when(own.getId()).thenReturn(INTEGRATION_ID);
        when(platformIntegrationRepository.findByTenantId(TENANT)).thenReturn(List.of(own));
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-01", null)).thenReturn(false);
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop saved = service.createShop(TENANT,
                new CreateShopCommand(INTEGRATION_ID.toString(), "AMAZON", "QA-01", "探针店", "NA", null));
        assertEquals(INTEGRATION_ID.toString(), saved.getIntegrationId());
    }

    @Test
    @DisplayName("站点被本租户其他店铺占用时拒绝")
    void createShopRejectsMarketplaceAlreadyBound() {
        when(shopRepository.existsByTenantIdAndShopCode(TENANT, "QA-02", null)).thenReturn(false);
        when(shopRepository.existsByTenantIdAndPlatformAndMarketplaceId(TENANT, "AMAZON", "ATVPDKIKX0DER"))
                .thenReturn(true);

        ValidationException e = assertThrows(ValidationException.class, () -> service.createShop(TENANT,
                new CreateShopCommand(null, "AMAZON", "QA-02", "探针店", "NA", "ATVPDKIKX0DER")));
        assertTrue(e.getMessage().contains("ATVPDKIKX0DER"));
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("写凭据：站点未变时不查唯一键，避免被自己的旧值挡住")
    void bindCredentialsSkipsMarketplaceCheckWhenUnchanged() {
        Shop shop = ownShop("AMAZON", "ATVPDKIKX0DER", "NA");
        when(shopRepository.findByIdAndTenantId(SHOP_ID, TENANT)).thenReturn(Optional.of(shop));
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop saved = service.bindCredentials(TENANT, SHOP_ID,
                new ShopCredentialCommand("key-2", null, null, null, null,
                        "ATVPDKIKX0DER", "eu", null, null, null));

        verify(shopRepository, never())
                .existsByTenantIdAndPlatformAndMarketplaceId(anyString(), anyString(), anyString());
        assertEquals("key-2", saved.getApiKey());
        assertEquals("EU", saved.getRegion());
        assertEquals(ShopAuthStatus.PENDING, saved.getAuthStatus());
    }

    @Test
    @DisplayName("写凭据：换站点时要查唯一键，冲突则不落库")
    void bindCredentialsChecksMarketplaceWhenChanged() {
        Shop shop = ownShop("AMAZON", "ATVPDKIKX0DER", "NA");
        when(shopRepository.findByIdAndTenantId(SHOP_ID, TENANT)).thenReturn(Optional.of(shop));
        when(shopRepository.existsByTenantIdAndPlatformAndMarketplaceId(TENANT, "AMAZON", "A2QN198DUBLIEO"))
                .thenReturn(true);

        assertThrows(ValidationException.class, () -> service.bindCredentials(TENANT, SHOP_ID,
                new ShopCredentialCommand(null, null, null, null, null,
                        "A2QN198DUBLIEO", null, null, null, null)));
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("写凭据的入参区域非法时先挡住，不打回授权状态")
    void bindCredentialsRejectsUnknownAmazonRegion() {
        Shop shop = ownShop("AMAZON", null, "NA");
        when(shopRepository.findByIdAndTenantId(SHOP_ID, TENANT)).thenReturn(Optional.of(shop));

        assertThrows(ValidationException.class, () -> service.bindCredentials(TENANT, SHOP_ID,
                new ShopCredentialCommand(null, null, null, null, null,
                        null, "MARS", null, null, null)));
        assertEquals(ShopAuthStatus.ACTIVE, shop.getAuthStatus(), "校验失败不应已经改动作废状态");
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除走带租户条件的删除，跨租户删不掉")
    void deleteShopUsesTenantScopedDelete() {
        Shop shop = ownShop("AMAZON", null, "NA");
        when(shopRepository.findByIdAndTenantId(SHOP_ID, TENANT)).thenReturn(Optional.of(shop));

        service.deleteShop(TENANT, SHOP_ID);
        verify(shopRepository).deleteByIdAndTenantId(SHOP_ID, TENANT);

        when(shopRepository.findByIdAndTenantId(SHOP_ID, OTHER_TENANT)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.deleteShop(OTHER_TENANT, SHOP_ID));
    }

    @Test
    @DisplayName("列表：不传平台走全量租户查询，传平台走带平台查询")
    void listShopsPicksQueryByPlatformPresence() {
        when(shopRepository.findByTenantId(TENANT)).thenReturn(List.of(ownShop("AMAZON", null, "NA")));
        when(shopRepository.findByTenantIdAndPlatform(TENANT, "SHOPEE")).thenReturn(List.of());

        assertEquals(1, service.listShops(TENANT, null).size());
        assertEquals(0, service.listShops(TENANT, "shopee").size());
        // 大小写归一是仓储侧做的（ShopRepositoryImpl 对 platform 大写），服务层原样透传
        verify(shopRepository).findByTenantIdAndPlatform(TENANT, "shopee");
    }
}
