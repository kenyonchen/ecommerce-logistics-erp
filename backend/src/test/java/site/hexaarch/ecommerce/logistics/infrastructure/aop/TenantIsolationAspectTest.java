package site.hexaarch.ecommerce.logistics.infrastructure.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 租户越权切面测试（ISS-050 / ISS-120）。
 * <p>
 * 本切面是 {@code @Before}，跑在应用服务体之前，因此它看到的是<b>客户端原始入参</b>：
 * {@code TenantAccessGuard.resolveWriteTenant} 里的归一化对它完全来不及生效。ISS-120 就是这条——
 * 租户用户带历史默认桶字面量发写请求，在这里就被判成跨租户越权（实测 400），
 * 连业务都进不去；而归一化放在这里之后，写路径才不至于把数据落进没有租户行的幻影桶。
 * </p>
 * <p>
 * 三条口径一起钉住：<b>字面量按"未指定"放行</b>、<b>真实他租户仍然拒绝</b>（负例，防止用"删掉校验"来骗过第一条）、
 * <b>平台管理员与无会话调用的原有豁免不变</b>。
 * </p>
 *
 * @author kenyon
 */
class TenantIsolationAspectTest {

    private static final String CURRENT_TENANT = "tenant-current";
    private static final String OTHER_TENANT = "tenant-other";
    private static final String LEGACY_BUCKET = "default-tenant";

    private TenantRepository tenantRepository;
    private TenantIsolationAspect aspect;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        aspect = new TenantIsolationAspect(tenantRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("租户用户带历史默认桶字面量不再被判越权")
    void legacyBucketLiteralIsNotTreatedAsCrossTenantAccess() {
        login(CURRENT_TENANT, "ROLE_TENANT_ADMIN");

        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(LEGACY_BUCKET)));
        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint("  " + LEGACY_BUCKET + " ")));
    }

    @Test
    @DisplayName("负例：真实他租户仍然拒绝，归一化没有把校验清空")
    void realOtherTenantIsStillRejected() {
        login(CURRENT_TENANT, "ROLE_TENANT_ADMIN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));
        assertEquals("TENANT_ACCESS_DENIED", exception.getErrorCode());
        // 拒绝来自越权判定而不是存在性判定，否则"他租户"会被答成"不存在"
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("本租户与未传租户照常放行")
    void ownTenantAndAbsentTenantPass() {
        login(CURRENT_TENANT, "ROLE_TENANT_ADMIN");

        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(CURRENT_TENANT)));
        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(null)));
        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint("   ")));
        // 本租户入参是最常见的形状，不能为它每次请求都查一遍库
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("平台管理员可跨真实租户，负例：点名一个 tenants 表里没有的租户被拒（ISS-135）")
    void platformAdminKeepsCrossTenantAccess() {
        login(CURRENT_TENANT, "ROLE_PLATFORM_ADMIN");
        when(tenantRepository.findById(OTHER_TENANT))
                .thenReturn(Optional.of(Tenant.create("探针租户", null, null, null)));

        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));
        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(LEGACY_BUCKET)));
        // 存在性校验只查那一个被点名的真实租户；字面量按未指定处理，不该被送去查库
        verify(tenantRepository).findById(OTHER_TENANT);
        verifyNoMoreInteractions(tenantRepository);
    }

    @Test
    @DisplayName("平台管理员点名的租户必须真实存在，否则数据会落进无主的幻影桶")
    void platformAdminCannotNameNonExistentTenant() {
        login(CURRENT_TENANT, "ROLE_PLATFORM_ADMIN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));

        assertEquals("TENANT_NOT_FOUND", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(OTHER_TENANT), exception.getMessage());
    }

    @Test
    @DisplayName("存在性校验只管写请求：GET 点名不存在的租户仍按空结果返回（ISS-135 的读侧边界）")
    void readRequestIsNotValidatedForTenantExistence() {
        login(CURRENT_TENANT, "ROLE_PLATFORM_ADMIN");
        bindRequest("GET");
        when(tenantRepository.findById(OTHER_TENANT)).thenReturn(Optional.empty());

        // 读一个不存在的租户不会产生幻影行，返回空页即可；把它判成 400 会改掉所有带 tenantId 的 GET 契约
        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("负例：同一入参换成写请求仍然拒（豁免不是靠关掉校验换来的）")
    void writeRequestStillValidatesTenantExistence() {
        login(CURRENT_TENANT, "ROLE_PLATFORM_ADMIN");
        bindRequest("POST");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));
        assertEquals("TENANT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    @DisplayName("无租户上下文（登录、事件线程、定时任务）不做校验")
    void callWithoutTenantContextIsSkipped() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")));

        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint(OTHER_TENANT)));
    }

    @Test
    @DisplayName("入参名不是 tenantId 的字符串不参与判定")
    void otherStringParameterIsNotInspected() {
        login(CURRENT_TENANT, "ROLE_TENANT_ADMIN");
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"warehouseName"});
        when(signature.toShortString()).thenReturn("WarehouseApplicationService.create(..)");
        when(joinPoint.getArgs()).thenReturn(new Object[]{LEGACY_BUCKET});

        assertDoesNotThrow(() -> aspect.beforeServiceMethod(joinPoint));
    }

    private void login(String tenantId, String role) {
        TenantContext.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(role)));
    }

    /**
     * 绑定一次 HTTP 请求上下文：存在性校验要区分读/写（ISS-135 的边界），
     * 而切面拿到的只有服务方法签名，动词只能从当前请求上读。
     */
    private static void bindRequest(String httpMethod) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(httpMethod, "/api/audit-logs")));
    }

    private static JoinPoint joinPoint(String requestedTenantId) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"tenantId", "customerName"});
        when(signature.toShortString()).thenReturn("CustomerApplicationService.createCustomer(..)");
        when(joinPoint.getArgs()).thenReturn(new Object[]{requestedTenantId, "探针客户"});
        return joinPoint;
    }
}
