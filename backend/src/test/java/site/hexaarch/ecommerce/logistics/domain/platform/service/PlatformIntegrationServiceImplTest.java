package site.hexaarch.ecommerce.logistics.domain.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 按对接协议区分必填凭据，以及库存推送的协议边界。
 *
 * @author kenyon
 */
class PlatformIntegrationServiceImplTest {

    private PlatformIntegrationRepository repository;
    private PlatformIntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformIntegrationRepository.class);
        when(repository.save(any(PlatformIntegration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PlatformIntegrationServiceImpl(repository, mock(InventoryRecordRepository.class),
                RestClient.builder(), new SimpleClientHttpRequestFactory());
    }

    private PlatformIntegration.Builder shopify() {
        return PlatformIntegration.builder()
                .tenantId("tenant-1")
                .platformName(PlatformName.SHOPIFY)
                .authType(PlatformAuthType.SHOPIFY)
                .storeUrl("https://demo.myshopify.com")
                .status(PlatformStatus.INACTIVE);
    }

    // ---------- 凭据必填项按协议分岔 ----------

    @Test
    void shopifyNeedsOnlyAccessToken() {
        // Admin API 不读 apiKey/apiSecret，表单留空必须能建单（两列 NOT NULL，归一化成空串）
        PlatformIntegration saved = service.createPlatformIntegration(
                shopify().accessToken("shpat_test").build());

        assertEquals("", saved.getApiKey());
        assertEquals("", saved.getApiSecret());
        assertEquals("shpat_test", saved.getAccessToken());
    }

    @Test
    void updateKeepsServerOwnedTimestampsWhenRequestOmitsThem() {
        UUID id = UUID.randomUUID();
        PlatformIntegration existing = shopify().id(id).accessToken("shpat_old")
                .createdAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(existing));

        // 控制器按请求体拼装聚合，时间戳恒为 null；曾经原样搬过去把 NOT NULL 列写成 null（冒烟 7.5）
        PlatformIntegration saved = service.updatePlatformIntegration(id, shopify().accessToken("shpat_new").build());

        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 0, 0), saved.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shopifyWithoutAccessTokenIsRejected() {
        PlatformIntegration integration = shopify().apiKey("ak").build();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPlatformIntegration(integration));
        assertEquals("PLATFORM_ACCESS_TOKEN_REQUIRED", error.getErrorCode());
    }

    @Test
    void customShopStillNeedsKeyAndSecret() {
        PlatformIntegration integration = PlatformIntegration.builder()
                .tenantId("tenant-1")
                .platformName(PlatformName.OTHER)
                .storeUrl("https://api.example-store.com")
                .status(PlatformStatus.INACTIVE)
                .build();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPlatformIntegration(integration));
        assertEquals("PLATFORM_API_KEY_REQUIRED", error.getErrorCode());
    }

    // ---------- 库存推送的协议边界 ----------

    @Test
    void inventoryPushRefusesNonCustomShopProtocol() {
        // {storeUrl}/inventory 是 CUSTOM_SHOP 的约定端点；对 Shopify 会 POST 到不存在的路径
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(
                shopify().accessToken("shpat_test").status(PlatformStatus.ACTIVE).apiKey("ak").build()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.syncInventoryToPlatform(id));

        assertEquals("PLATFORM_INVENTORY_PUSH_UNSUPPORTED", error.getErrorCode());
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
