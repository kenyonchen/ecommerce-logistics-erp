package site.hexaarch.ecommerce.logistics.application.service.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.repository.AuditLogRepository;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计检索服务的租户收敛测试（BR-12，R-6 口径）。
 * <p>
 * 审计行含操作人、IP、目标 ID 与失败原因，属于跨租户敏感数据：读端点虽已用
 * {@code hasRole('PLATFORM_ADMIN')} 守住，服务层仍必须自己收敛一次（纵深防御，ISS-050 同口径）——
 * 否则一旦有人放宽 URL 门槛，整个平台的审计数据就直接可翻了。
 * </p>
 *
 * @author kenyon
 */
class AuditApplicationServiceTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private AuditLogRepository auditLogRepository;
    private AuditApplicationService service;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        service = new AuditApplicationService(auditLogRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    private void login(String tenantId, String... roles) {
        TenantContext.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(roles)));
    }

    @Test
    @DisplayName("写入原样交给仓储，由仓储生成主键")
    void recordDelegatesToRepository() {
        AuditLog entry = AuditLog.builder().action("USER_CREATE").result(AuditResult.SUCCESS).build();
        AuditLog saved = entry.toBuilder().id("audit-1").build();
        when(auditLogRepository.save(entry)).thenReturn(saved);

        assertEquals("audit-1", service.record(entry).getId());
        verify(auditLogRepository).save(entry);
    }

    @Test
    @DisplayName("租户管理员的检索被收敛到自己的租户")
    void searchIsScopedToLoginTenant() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");
        when(auditLogRepository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(0, 20));

        service.search(null, null, null, 0, 20);

        verify(auditLogRepository).search(eq(TENANT_B), any(), any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("租户管理员显式传别家租户时拒绝，而不是返回空列表")
    void searchRejectsCrossTenantRequest() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.search(TENANT_A, "USER_LOGIN", null, 0, 20));
        assertEquals("TENANT_ACCESS_DENIED", exception.getErrorCode());
    }

    @Test
    @DisplayName("平台管理员不带 tenantId 时可看全平台")
    void platformAdminSeesWholePlatform() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN");
        when(auditLogRepository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(0, 20));

        service.search(null, null, null, 0, 20);

        verify(auditLogRepository).search(eq(null), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("最近活动同样按租户收敛")
    void findRecentIsScopedToo() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");
        when(auditLogRepository.findRecent(any(), anyInt())).thenReturn(List.of());

        service.findRecent(null, 10);

        verify(auditLogRepository).findRecent(TENANT_B, 10);
        assertThrows(BusinessException.class, () -> service.findRecent(TENANT_A, 10));
    }

    @Test
    @DisplayName("结果参数大小写不敏感，非法值报 400 而不是当成「不限」")
    void resultParameterIsValidated() {
        when(auditLogRepository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(0, 20));

        service.search(TENANT_A, null, "success", 0, 20);
        verify(auditLogRepository).search(eq(TENANT_A), eq(null), eq(AuditResult.SUCCESS), eq(0), eq(20));

        service.search(TENANT_A, null, " FAILURE ", 0, 20);
        verify(auditLogRepository).search(eq(TENANT_A), eq(null), eq(AuditResult.FAILURE), eq(0), eq(20));

        assertThrows(ValidationException.class, () -> service.search(TENANT_A, null, "MAYBE", 0, 20));
    }

    @Test
    @DisplayName("无请求上下文（内部调用）时不强行收敛，保持平台视角")
    void withoutTenantContextNoScopingHappens() {
        when(auditLogRepository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(0, 20));

        service.search(TENANT_A, "USER_CREATE", null, 1, 50);

        verify(auditLogRepository).search(TENANT_A, "USER_CREATE", null, 1, 50);
    }

    @Test
    @DisplayName("失败计数固定按 FAILURE 统计，锁定窗口起点原样下传")
    void countsFailuresOnlyForFailureResult() {
        List<String> actions = List.of("AUTH_LOGIN");
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        when(auditLogRepository.countFailuresSince(actions, "admin", AuditResult.FAILURE, since)).thenReturn(4L);

        assertEquals(4L, service.countRecentFailures(actions, "admin", since));

        ArgumentCaptor<AuditResult> captor = ArgumentCaptor.forClass(AuditResult.class);
        verify(auditLogRepository).countFailuresSince(eq(actions), eq("admin"), captor.capture(), eq(since));
        assertEquals(AuditResult.FAILURE, captor.getValue());
    }

    @Test
    @DisplayName("无成功记录时最近成功时间为 null（锁定窗口退到固定窗口）")
    void lastSuccessTimePassesThroughNull() {
        when(auditLogRepository.findMostRecentSuccessTime(List.of("AUTH_LOGIN"), "admin")).thenReturn(null);

        assertNull(service.findLastSuccessTime(List.of("AUTH_LOGIN"), "admin"));
        assertTrue(service.findLastSuccessTime(List.of("AUTH_LOGIN"), "admin") == null);
    }
}
