package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import site.hexaarch.ecommerce.logistics.application.service.audit.AuditApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.config.SystemConfigApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.audit.AuditRequestContext;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录失败锁定与认证留痕测试（security-design §5 / P1-c）。
 * <p>
 * 锁定以 audit_logs 为唯一计数源，因此窗口起点算法是本测试的核心：
 * 必须锚在「上次成功登录之后」，否则历史失败会永久累计，账号被判过一次就再也解不开
 * —— 这类缺陷在真实运维里表现为「用户改对了密码仍然登不进」，且日志上看不出原因。
 * </p>
 *
 * @author kenyon
 */
class LoginSecurityServiceTest {

    private static final String USERNAME = "admin";
    private static final int THRESHOLD = 5;

    private AuditApplicationService auditApplicationService;
    private SystemConfigApplicationService systemConfigApplicationService;
    private AuditRequestContext auditRequestContext;
    private LoginSecurityService service;

    @BeforeEach
    void setUp() {
        auditApplicationService = mock(AuditApplicationService.class);
        systemConfigApplicationService = mock(SystemConfigApplicationService.class);
        auditRequestContext = mock(AuditRequestContext.class);
        service = new LoginSecurityService(auditApplicationService, systemConfigApplicationService,
                auditRequestContext);
        when(systemConfigApplicationService.maxLoginAttempts()).thenReturn(THRESHOLD);
        when(auditRequestContext.clientIp()).thenReturn("10.0.0.8");
        when(auditRequestContext.traceId()).thenReturn("trace-1");
    }

    /**
     * 让「统计窗口内失败」返回指定值：窗口起点由被测实现决定，这里用 any() 接单。
     */
    private void stubFailures(long failures) {
        when(auditApplicationService.countRecentFailures(anyList(), eq(USERNAME), any(LocalDateTime.class)))
                .thenReturn(failures);
    }

    @Test
    @DisplayName("失败次数达到阈值即拒绝登录")
    void blocksAtThreshold() {
        stubFailures(THRESHOLD);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assertLoginAllowed(USERNAME));
        assertEquals("TOO_MANY_LOGIN_ATTEMPTS", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("15"), "提示里要写清等待时长: " + exception.getMessage());
    }

    @Test
    @DisplayName("失败次数未达阈值时放行")
    void allowsBelowThreshold() {
        stubFailures(THRESHOLD - 1);

        service.assertLoginAllowed(USERNAME);
    }

    @Test
    @DisplayName("阈值配成 0 表示关闭锁定：一次查询都不做")
    void zeroThresholdDisablesLockout() {
        when(systemConfigApplicationService.maxLoginAttempts()).thenReturn(0);

        service.assertLoginAllowed(USERNAME);

        verifyNoInteractions(auditApplicationService);
    }

    @Test
    @DisplayName("无历史成功记录时，窗口起点为 now-15 分钟")
    void windowStartsAtFixedWindowWithoutPriorSuccess() {
        when(auditApplicationService.findLastSuccessTime(AuditActions.AUTH_FAILURE_ACTIONS, USERNAME))
                .thenReturn(null);
        stubFailures(0);

        service.assertLoginAllowed(USERNAME);

        LocalDateTime since = capturedWindowStart();
        long secondsAgo = Duration.between(since, LocalDateTime.now()).toSeconds();
        assertTrue(secondsAgo >= 14 * 60 && secondsAgo <= 16 * 60,
                "窗口起点应落在 now-15min 附近，实际 " + secondsAgo + " 秒前");
    }

    @Test
    @DisplayName("上次成功登录会重置窗口：只统计该成功之后的失败")
    void priorSuccessResetsTheWindow() {
        LocalDateTime lastSuccess = LocalDateTime.now().minusMinutes(2);
        when(auditApplicationService.findLastSuccessTime(AuditActions.AUTH_FAILURE_ACTIONS, USERNAME))
                .thenReturn(lastSuccess);
        stubFailures(1);

        service.assertLoginAllowed(USERNAME);

        assertEquals(lastSuccess, capturedWindowStart(), "窗口起点必须是上次成功的时间，而不是固定回看 15 分钟");
    }

    @Test
    @DisplayName("上次成功早于固定窗口时，窗口不会被拉长到成功之前")
    void staleSuccessDoesNotExtendWindow() {
        when(auditApplicationService.findLastSuccessTime(AuditActions.AUTH_FAILURE_ACTIONS, USERNAME))
                .thenReturn(LocalDateTime.now().minusDays(3));
        stubFailures(0);

        service.assertLoginAllowed(USERNAME);

        long secondsAgo = Duration.between(capturedWindowStart(), LocalDateTime.now()).toSeconds();
        assertTrue(secondsAgo >= 14 * 60 && secondsAgo <= 16 * 60,
                "早于窗口的成功时间不该把回看期拉长，实际 " + secondsAgo + " 秒前");
    }

    @Test
    @DisplayName("用户名为空时不做锁定判断（交给认证层报凭据错误）")
    void blankUsernameSkipsLockout() {
        service.assertLoginAllowed(null);
        service.assertLoginAllowed("  ");

        verifyNoInteractions(auditApplicationService);
    }

    @Test
    @DisplayName("登录成功留痕：SUCCESS、租户取登录用户、动作码与检索口径一致")
    void recordsSuccessfulLogin() {
        when(auditRequestContext.actorId(USERNAME)).thenReturn("user-1");

        service.recordLoginAttempt(USERNAME, TENANT(), null);

        AuditLog entry = capturedAudit();
        assertEquals(AuditActions.AUTH_LOGIN, entry.getAction());
        assertEquals("登录成功", entry.getActionLabel());
        assertEquals(AuditResult.SUCCESS, entry.getResult());
        assertEquals(USERNAME, entry.getActorName());
        assertEquals("user-1", entry.getActorId());
        assertEquals(TENANT(), entry.getTenantId());
        assertEquals("AuthSession", entry.getTargetType());
        assertEquals(USERNAME, entry.getTargetId());
        assertEquals("10.0.0.8", entry.getIp());
        assertEquals("trace-1", entry.getTraceId());
    }

    @Test
    @DisplayName("登录失败留痕：FAILURE + 异常摘要，租户未知时退到请求上下文")
    void recordsFailedLogin() {
        when(auditRequestContext.tenantId()).thenReturn("tenant-from-header");

        service.recordLoginAttempt("ghost", null, new BadCredentialsException("Invalid username or password"));

        AuditLog entry = capturedAudit();
        assertEquals(AuditResult.FAILURE, entry.getResult());
        assertEquals("登录失败", entry.getActionLabel());
        assertEquals("BadCredentialsException: Invalid username or password", entry.getErrorMessage());
        assertEquals("ghost", entry.getActorName());
        assertEquals("tenant-from-header", entry.getTenantId());
        // 失败原因只记异常类型与消息，绝不把提交的口令写进审计表
        assertTrue(!entry.getErrorMessage().contains("password="), entry.getErrorMessage());
    }

    @Test
    @DisplayName("失败时已知租户优先于请求上下文（避免伪造头污染归属）")
    void knownTenantWinsOverRequestContext() {
        when(auditRequestContext.tenantId()).thenReturn("tenant-from-header");

        service.recordLoginAttempt(USERNAME, TENANT(), new RuntimeException("boom"));

        assertEquals(TENANT(), capturedAudit().getTenantId());
    }

    @Test
    @DisplayName("刷新令牌留痕用独立动作码，与登录失败锁定互不干扰")
    void recordsRefreshAttempt() {
        service.recordRefreshAttempt(USERNAME, TENANT(), null);

        AuditLog entry = capturedAudit();
        assertEquals(AuditActions.AUTH_REFRESH, entry.getAction());
        assertEquals("刷新令牌成功", entry.getActionLabel());
        assertEquals(AuditResult.SUCCESS, entry.getResult());
    }

    @Test
    @DisplayName("锁定判定只统计 AUTH_LOGIN，不把刷新失败算成爆破")
    void lockoutCountsLoginActionOnly() {
        stubFailures(0);

        service.assertLoginAllowed(USERNAME);

        assertEquals(List.of(AuditActions.AUTH_LOGIN), AuditActions.AUTH_FAILURE_ACTIONS,
                "锁定计数源只含登录，刷新失败（AUTH_REFRESH）不得推高锁定");
        verify(auditApplicationService).countRecentFailures(eq(AuditActions.AUTH_FAILURE_ACTIONS),
                eq(USERNAME), any(LocalDateTime.class));
    }

    private static String TENANT() {
        return "tenant-a";
    }

    private LocalDateTime capturedWindowStart() {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditApplicationService).countRecentFailures(anyList(), eq(USERNAME), captor.capture());
        return captor.getValue();
    }

    private AuditLog capturedAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditApplicationService).record(captor.capture());
        return captor.getValue();
    }
}
