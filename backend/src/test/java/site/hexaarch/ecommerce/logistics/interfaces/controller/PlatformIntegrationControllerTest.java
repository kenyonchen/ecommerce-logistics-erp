package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.platform.PlatformIntegrationApplicationService;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import site.hexaarch.ecommerce.logistics.interfaces.controller.platform.PlatformIntegrationController;
import site.hexaarch.ecommerce.logistics.interfaces.handler.GlobalExceptionHandler;

class PlatformIntegrationControllerTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Mock
    private PlatformIntegrationApplicationService platformIntegrationApplicationService;

    @InjectMocks
    private PlatformIntegrationController platformIntegrationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 按 ID 的写操作在控制器入口做租户归属校验（ISS-122），抛出的 EntityNotFoundException
        // 由全局异常处理器映射成 404；standaloneSetup 不会自动带上 @ControllerAdvice，需显式注册
        mockMvc = MockMvcBuilders.standaloneSetup(platformIntegrationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clearTenantId();
        SecurityContextHolder.clearContext();
    }

    /**
     * 模拟租户用户登录：只带租户上下文，不带平台管理员角色
     */
    private void loginAsTenantUser(String tenantId) {
        TenantContext.setTenantId(tenantId);
    }

    /**
     * 模拟平台管理员登录：ROLE_PLATFORM_ADMIN 可跨租户
     */
    private void loginAsPlatformAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "platform-admin", "n/a",
                AuthorityUtils.createAuthorityList("ROLE_PLATFORM_ADMIN")));
    }

    private PlatformIntegration mockIntegration(String tenantId) {
        return PlatformIntegration.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();
    }

    /**
     * 按 ID 的写操作会先做租户归属校验（ISS-122），这里以租户用户身份登录并把该 ID 的数据
     * 挂到登录租户下，让用例聚焦在业务行为而不是守卫本身。
     */
    private PlatformIntegration stubOwnedIntegration(UUID id) {
        loginAsTenantUser(TENANT_A);
        PlatformIntegration owned = mockIntegration(TENANT_A);
        when(platformIntegrationApplicationService.getPlatformIntegrationById(eq(id))).thenReturn(owned);
        return owned;
    }

    @Test
    void testCreatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.createPlatformIntegration(any(PlatformIntegration.class)))
                .thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + platformId + "\",\"platformName\":\"AMAZON\",\"apiKey\":\"test-api-key\",\"apiSecret\":\"test-api-secret\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(platformId.toString()));
    }

    @Test
    void testGetPlatformIntegrationById() throws Exception {
        loginAsTenantUser(TENANT_A);
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .tenantId(TENANT_A)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.getPlatformIntegrationById(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(get("/api/platform-integrations/" + platformId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(platformId.toString()));
    }

    /**
     * 读接口已放开到登录用户，但租户用户不得读取其他租户的平台集成（BR-02：出参仍只给掩码）。
     */
    @Test
    void testGetPlatformIntegrationById_TenantAccessDenied() throws Exception {
        loginAsTenantUser(TENANT_A);
        UUID platformId = UUID.randomUUID();
        PlatformIntegration otherTenantIntegration = mockIntegration(TENANT_B);

        when(platformIntegrationApplicationService.getPlatformIntegrationById(eq(platformId)))
                .thenReturn(otherTenantIntegration);

        mockMvc.perform(get("/api/platform-integrations/" + platformId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void testGetAllPlatformIntegrations() throws Exception {
        loginAsTenantUser(TENANT_A);
        PlatformIntegration mockPlatform = mockIntegration(TENANT_A);

        when(platformIntegrationApplicationService.getPlatformIntegrationsByTenant(TENANT_A))
                .thenReturn(List.of(mockPlatform));

        mockMvc.perform(get("/api/platform-integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /**
     * 租户用户即使显式传别的 tenantId，也只按登录租户过滤（横向越权防护）。
     */
    @Test
    void testGetAllPlatformIntegrations_IgnoresRequestedTenantId() throws Exception {
        loginAsTenantUser(TENANT_A);
        PlatformIntegration mockPlatform = mockIntegration(TENANT_A);

        when(platformIntegrationApplicationService.getPlatformIntegrationsByTenant(TENANT_A))
                .thenReturn(List.of(mockPlatform));

        mockMvc.perform(get("/api/platform-integrations").param("tenantId", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /**
     * 平台管理员不传 tenantId 时仍可跨租户查看全量。
     */
    @Test
    void testGetAllPlatformIntegrations_PlatformAdminSeesAllTenants() throws Exception {
        loginAsPlatformAdmin();
        when(platformIntegrationApplicationService.getAllPlatformIntegrations())
                .thenReturn(List.of(mockIntegration(TENANT_A), mockIntegration(TENANT_B)));

        mockMvc.perform(get("/api/platform-integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void testUpdatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("updated-api-key")
                .apiSecret("updated-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.updatePlatformIntegration(eq(platformId), any(PlatformIntegration.class)))
                .thenReturn(mockPlatform);

        mockMvc.perform(put("/api/platform-integrations/" + platformId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + platformId + "\",\"platformName\":\"AMAZON\",\"apiKey\":\"updated-api-key\",\"apiSecret\":\"updated-api-secret\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // BR-02：出参只给掩码，凭据明文一律不回传
                .andExpect(jsonPath("$.data.apiKeyMasked").value("upd****-key"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("updated-api-secret"),
                        "响应体不得包含凭据明文"));
    }

    @Test
    void testDeletePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);

        mockMvc.perform(delete("/api/platform-integrations/" + platformId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 负例：租户用户拿着他租户的集成 ID 做写操作，按 ID 的写接口一律 404
     * （跨租户与不存在同码，不给他租户资源存在性的信号）。
     */
    @Test
    void testDeletePlatformIntegration_TenantAccessDenied() throws Exception {
        loginAsTenantUser(TENANT_A);
        UUID platformId = UUID.randomUUID();
        when(platformIntegrationApplicationService.getPlatformIntegrationById(eq(platformId)))
                .thenReturn(mockIntegration(TENANT_B));

        mockMvc.perform(delete("/api/platform-integrations/" + platformId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));

        verify(platformIntegrationApplicationService, never()).deletePlatformIntegration(any());
    }

    @Test
    void testActivatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.activatePlatformIntegration(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void testDeactivatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.INACTIVE)
                .build();

        when(platformIntegrationApplicationService.deactivatePlatformIntegration(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void testSyncOrdersFromPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testSyncProductsFromPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testSyncInventoryToPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();
        stubOwnedIntegration(platformId);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}