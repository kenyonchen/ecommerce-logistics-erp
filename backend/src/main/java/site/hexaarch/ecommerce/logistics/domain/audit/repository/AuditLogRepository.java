package site.hexaarch.ecommerce.logistics.domain.audit.repository;

import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志仓储：只追加，不更新、不删除。
 *
 * @author kenyon
 */
public interface AuditLogRepository {

    /**
     * 追加一条审计记录
     */
    AuditLog save(AuditLog auditLog);

    /**
     * 分页检索
     *
     * @param tenantId 租户ID，null 表示不按租户过滤（平台管理员视角）
     * @param action   动作码精确匹配，null/空表示不限
     * @param result   结果，null 表示不限
     */
    PageResult<AuditLog> search(String tenantId, String action, AuditResult result, int page, int size);

    /**
     * 取最近若干条（平台看板「最近活动」）
     */
    List<AuditLog> findRecent(String tenantId, int limit);

    /**
     * 统计某账号在指定时刻之后的失败次数，用于登录失败锁定
     *
     * @param actions 计入失败的动作风集合
     */
    long countFailuresSince(List<String> actions, String actorName, AuditResult result, LocalDateTime since);

    /**
     * 取某操作人最近一次成功动作的时间，用于把锁定窗口收敛到"上次成功之后"
     * （否则历史失败会永久累计，账号一旦被判过就再也解不开）
     *
     * @return 最近成功时间，无记录时为 null
     */
    LocalDateTime findMostRecentSuccessTime(List<String> actions, String actorName);
}
