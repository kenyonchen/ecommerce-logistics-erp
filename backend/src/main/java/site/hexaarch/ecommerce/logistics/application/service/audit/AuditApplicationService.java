package site.hexaarch.ecommerce.logistics.application.service.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.repository.AuditLogRepository;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志应用服务：写入与检索。
 * <p>
 * 写入走 {@link Propagation#REQUIRES_NEW}：被审计的业务事务失败回滚时，
 * 失败留痕必须留下（越权与提权探测的信号就在失败侧），不能跟着业务数据一起回滚。
 * </p>
 * <p>
 * 检索一律先过 {@link TenantAccessGuard}：非平台管理员只能看本租户记录，
 * 平台管理员不带 tenantId 时看全平台。读端点本身另有
 * {@code hasRole('PLATFORM_ADMIN')} 门槛，这里是第二道防线（纵深防御，ISS-050 同类口径）。
 * </p>
 *
 * @author kenyon
 */
@Service
public class AuditApplicationService {

    private final AuditLogRepository auditLogRepository;

    public AuditApplicationService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 追加一条审计记录
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    /**
     * 分页检索审计记录
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLog> search(String tenantId, String action, String result, int page, int size) {
        return auditLogRepository.search(TenantAccessGuard.scopedTenantId(tenantId), action,
                parseResult(result), page, size);
    }

    /**
     * 最近若干条审计记录
     */
    @Transactional(readOnly = true)
    public List<AuditLog> findRecent(String tenantId, int limit) {
        return auditLogRepository.findRecent(TenantAccessGuard.scopedTenantId(tenantId), limit);
    }

    /**
     * 统计某操作人在时间窗内的失败次数（登录锁定用）
     */
    @Transactional(readOnly = true)
    public long countRecentFailures(List<String> actions, String actorName, LocalDateTime since) {
        return auditLogRepository.countFailuresSince(actions, actorName, AuditResult.FAILURE, since);
    }

    /**
     * 某操作人最近一次成功动作的时间（把锁定窗口收敛到"上次成功之后"）
     */
    @Transactional(readOnly = true)
    public LocalDateTime findLastSuccessTime(List<String> actions, String actorName) {
        return auditLogRepository.findMostRecentSuccessTime(actions, actorName);
    }

    private AuditResult parseResult(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return AuditResult.valueOf(result.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("审计结果只能是 SUCCESS 或 FAILURE: " + result);
        }
    }
}
