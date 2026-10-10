package site.hexaarch.ecommerce.logistics.interfaces.controller.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.application.service.platform.PlatformIntegrationApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.dto.platform.PlatformIntegrationSaveRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台集成写路径的租户归属测试（ISS-121 / ISS-122）。
 * <p>
 * 这个聚合的写入形状和别人不一样：请求体的 {@code tenantId} 被 {@code toAggregate} 塞进聚合，
 * 服务方法的形参是聚合而不是 {@code tenantId}，所以 {@code TenantIsolationAspect} 的入参校验
 * 与 {@code TenantWriteFallbackGuardTest} 的源码扫描<b>两条都够不着它</b>。实测库里因此躺着
 * 35 条 {@code tenant_id='default-tenant'} 的幻影行（{@code tenants} 表没有这一行，谁都查不出来），
 * 连 AES-GCM 加密后的平台凭据一起进了死桶。这里把归属钉在控制器入口：
 * 创建走 {@code TenantAccessGuard.resolveWriteTenant}（哨兵按未指定处理、显式他租户拒绝），
 * 更新归属由既有行决定，按 ID 的写操作一律先判归属——{@code platform:manage} 可以发给租户角色，
 * 只靠 {@code @PreAuthorize} 拦不住横向越权。
 * </p>
 *
 * @author kenyon
 */
class PlatformIntegrationControllerTenantGuardTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String LEGACY_BUCKET = "default-tenant";
    /** tenants 表里没有这一行的租户ID，客户端却可以点名它（ISS-135） */
    private static final String TENANT_GHOST = "11111111-2222-3333-4444-555555555555";

    private PlatformIntegrationApplicationService service;
    private TenantRepository tenantRepository;
    private PlatformIntegrationController controller;

    @BeforeEach
    void setUp() {
        service = mock(PlatformIntegrationApplicationService.class);
        tenantRepository = mock(TenantRepository.class);
        controller = new PlatformIntegrationController(service, tenantRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    @Test
    @DisplayName("创建带历史桶字面量时落登录租户，不落幻影桶")
    void createWithLegacyLiteralLandsOnSessionTenant() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");
        when(service.createPlatformIntegration(any())).thenAnswer(call -> call.getArgument(0));

        controller.createPlatformIntegration(saveRequest(LEGACY_BUCKET));

        assertEquals(TENANT_B, capturedCreatedTenantId());
    }

    @Test
    @DisplayName("平台管理员带字面量时落自己的租户，绝不落幻影桶")
    void createByPlatformAdminNeverWritesPhantomBucket() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN");
        when(service.createPlatformIntegration(any())).thenAnswer(call -> call.getArgument(0));

        controller.createPlatformIntegration(saveRequest(LEGACY_BUCKET));

        assertEquals(TENANT_A, capturedCreatedTenantId());
    }

    @Test
    @DisplayName("负例：租户用户显式传他租户创建仍被拒（映射为 400），归一化没有把守卫清空")
    void createForExplicitOtherTenantIsStillRejected() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        ResponseEntity<?> response = controller.createPlatformIntegration(saveRequest(TENANT_A));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(service, never()).createPlatformIntegration(any());
    }

    @Test
    @DisplayName("负例：平台管理员点名一个 tenants 表里不存在的租户，创建被拒（ISS-135）")
    void createForNonexistentTenantIsRejected() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN");

        ResponseEntity<?> response = controller.createPlatformIntegration(saveRequest(TENANT_GHOST));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(tenantRepository).findById(TENANT_GHOST);
        verify(service, never()).createPlatformIntegration(any());
    }

    @Test
    @DisplayName("平台管理员代客写进真实存在的租户照常放行，存在性校验不是见显式租户就拒")
    void createForExistingTenantProceeds() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN");
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(Tenant.create("探针租户", null, null, null)));
        when(service.createPlatformIntegration(any())).thenAnswer(call -> call.getArgument(0));

        controller.createPlatformIntegration(saveRequest(TENANT_B));

        assertEquals(TENANT_B, capturedCreatedTenantId());
    }

    @Test
    @DisplayName("更新时归属由既有行决定，请求体不能把行迁走")
    void updateKeepsTheExistingRowTenant() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN");
        UUID id = UUID.randomUUID();
        when(service.getPlatformIntegrationById(id)).thenReturn(integration(TENANT_A));
        when(service.updatePlatformIntegration(any(), any())).thenAnswer(call -> call.getArgument(1));

        controller.updatePlatformIntegration(id, saveRequest(LEGACY_BUCKET));

        ArgumentCaptor<PlatformIntegration> captor = ArgumentCaptor.forClass(PlatformIntegration.class);
        verify(service).updatePlatformIntegration(org.mockito.Mockito.eq(id), captor.capture());
        assertEquals(TENANT_A, captor.getValue().getTenantId());
    }

    @Test
    @DisplayName("按 ID 的写操作跨租户一律 404，不用状态码差异确认存在性")
    void writeByIdAcrossTenantIsHiddenAsNotFound() {
        login(TENANT_B, "ROLE_TENANT_ADMIN", "platform:manage");
        UUID id = UUID.randomUUID();
        when(service.getPlatformIntegrationById(id)).thenReturn(integration(TENANT_A));

        assertThrows(EntityNotFoundException.class, () -> controller.deletePlatformIntegration(id));
        assertThrows(EntityNotFoundException.class, () -> controller.activatePlatformIntegration(id));
        assertThrows(EntityNotFoundException.class, () -> controller.deactivatePlatformIntegration(id));
        assertThrows(EntityNotFoundException.class, () -> controller.testConnection(id));
        assertThrows(EntityNotFoundException.class,
                () -> controller.updatePlatformIntegration(id, saveRequest(TENANT_B)));
        verify(service, never()).deletePlatformIntegration(any());
        verify(service, never()).activatePlatformIntegration(any());
        verify(service, never()).deactivatePlatformIntegration(any());
        verify(service, never()).testConnection(any());
        verify(service, never()).updatePlatformIntegration(any(), any());
    }

    @Test
    @DisplayName("本租户的按 ID 写操作照常放行")
    void writeByIdWithinOwnTenantProceeds() {
        login(TENANT_B, "ROLE_TENANT_ADMIN", "platform:manage");
        UUID id = UUID.randomUUID();
        when(service.getPlatformIntegrationById(id)).thenReturn(integration(TENANT_B));

        controller.deletePlatformIntegration(id);

        verify(service).deletePlatformIntegration(id);
    }

    @Test
    @DisplayName("负例：查无此 ID 同样 404，不会把空聚合送去判归属")
    void missingIdIsNotFoundRatherThanNullPointer() {
        login(TENANT_B, "ROLE_TENANT_ADMIN", "platform:manage");
        UUID id = UUID.randomUUID();
        when(service.getPlatformIntegrationById(id)).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> controller.deletePlatformIntegration(id));
        verify(service, never()).deletePlatformIntegration(any());
    }

    private String capturedCreatedTenantId() {
        ArgumentCaptor<PlatformIntegration> captor = ArgumentCaptor.forClass(PlatformIntegration.class);
        verify(service).createPlatformIntegration(captor.capture());
        return captor.getValue().getTenantId();
    }

    private static PlatformIntegrationSaveRequest saveRequest(String tenantId) {
        PlatformIntegrationSaveRequest request = new PlatformIntegrationSaveRequest();
        request.setTenantId(tenantId);
        request.setPlatformName(PlatformName.SHOPIFY);
        request.setStoreUrl("https://probe.myshopify.com");
        return request;
    }

    private static PlatformIntegration integration(String tenantId) {
        return PlatformIntegration.builder().tenantId(tenantId).platformName(PlatformName.SHOPIFY).build();
    }

    private static void login(String tenantId, String... roles) {
        TenantContext.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(roles)));
    }
}
