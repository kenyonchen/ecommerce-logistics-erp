package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.audit.AuditApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.config.SystemConfigApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.audit.AuditRequestContext;

import java.time.LocalDateTime;

/**
 * 登录与令牌刷新的安全留痕 + 失败锁定。
 * <p>
 * 认证链路上的操作发生在 SecurityContext 建立之前（登录）或匿名请求里（刷新），
 * 注解式审计取不到操作人，因此这里显式记录；动作码仍统一用
 * {@link AuditActions}，保证与切面写出来的记录同表同检索口径。
 * </p>
 * <p>
 * 失败锁定以 audit_logs 为计数源（不再新增一张失败计数的表）：
 * 窗口起点取「上次成功登录」与「锁定窗口起点」的较晚者 —— 只按固定窗口计数会让历史失败
 * 永久累计，账号被判过一次就再也解不开。
 * </p>
 *
 * @author kenyon
 */
@Service
public class LoginSecurityService {

    /**
     * 登录失败计数的最大回看时长；超过这个时长的失败不再计入锁定
     */
    private static final int LOCK_WINDOW_MINUTES = 15;

    private final AuditApplicationService auditApplicationService;
    private final SystemConfigApplicationService systemConfigApplicationService;
    private final AuditRequestContext auditRequestContext;

    public LoginSecurityService(AuditApplicationService auditApplicationService,
                                SystemConfigApplicationService systemConfigApplicationService,
                                AuditRequestContext auditRequestContext) {
        this.auditApplicationService = auditApplicationService;
        this.systemConfigApplicationService = systemConfigApplicationService;
        this.auditRequestContext = auditRequestContext;
    }

    /**
     * 登录前校验：失败次数达到阈值则拒绝，阈值来自系统配置（{@code <= 0} 表示关闭锁定）
     */
    public void assertLoginAllowed(String username) {
        int maxAttempts = systemConfigApplicationService.maxLoginAttempts();
        if (maxAttempts <= 0 || username == null || username.isBlank()) {
            return;
        }
        long failures = countRecentFailures(username);
        if (failures >= maxAttempts) {
            throw new BusinessException("登录失败次数过多，请在 " + LOCK_WINDOW_MINUTES + " 分钟后重试",
                    "TOO_MANY_LOGIN_ATTEMPTS");
        }
    }

    /**
     * 窗口内失败次数（供测试与看板复用）
     */
    public long countRecentFailures(String username) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(LOCK_WINDOW_MINUTES);
        LocalDateTime lastSuccess = auditApplicationService.findLastSuccessTime(
                AuditActions.AUTH_FAILURE_ACTIONS, username);
        LocalDateTime since = lastSuccess == null || lastSuccess.isBefore(windowStart)
                ? windowStart : lastSuccess;
        return auditApplicationService.countRecentFailures(AuditActions.AUTH_FAILURE_ACTIONS, username, since);
    }

    /**
     * 记录一次登录尝试（成功与失败都记）
     *
     * @param userTenantId 登录成功时已知的使用者租户；失败时传 null
     */
    public void recordLoginAttempt(String username, String userTenantId, Throwable failure) {
        recordAuthAttempt(AuditActions.AUTH_LOGIN, "登录", username, userTenantId, failure);
    }

    public void recordRefreshAttempt(String username, String userTenantId, Throwable failure) {
        recordAuthAttempt(AuditActions.AUTH_REFRESH, "刷新令牌", username, userTenantId, failure);
    }

    private void recordAuthAttempt(String action, String label, String username,
                                   String userTenantId, Throwable failure) {
        String tenantId = userTenantId != null && !userTenantId.isBlank() ? userTenantId : auditRequestContext.tenantId();
        auditApplicationService.record(AuditLog.builder()
                .tenantId(tenantId)
                .actorId(auditRequestContext.actorId(username))
                .actorName(username)
                .action(action)
                .actionLabel(failure == null ? label + "成功" : label + "失败")
                .targetType("AuthSession")
                .targetId(username)
                .result(failure == null ? AuditResult.SUCCESS : AuditResult.FAILURE)
                .errorMessage(failure == null ? null
                        : AuditRequestContext.truncate(failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : ": " + failure.getMessage())))
                .ip(auditRequestContext.clientIp())
                .traceId(auditRequestContext.traceId())
                .build());
    }
}
