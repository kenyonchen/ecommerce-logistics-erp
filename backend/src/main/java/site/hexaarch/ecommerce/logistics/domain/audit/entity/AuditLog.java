package site.hexaarch.ecommerce.logistics.domain.audit.entity;

import lombok.Builder;
import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;

import java.time.LocalDateTime;

/**
 * 审计日志条目：一次敏感操作（身份、权限、租户、凭据、金额类变更）的不可变留痕。
 * <p>
 * 落地的是 docs/tech/security-design.md §7 要求的 audit_logs 目标形态
 * （actor、tenant、action、target、ip、traceId、结果）。
 * 字段全部只写一次、不再修改，因此用不可变值对象而非聚合根建模。
 * </p>
 * <p>
 * {@code detail} 只允许放操作语义描述，禁止放入口令、令牌、平台凭据等敏感值
 * —— 本对象的 getter 会被 Jackson 序列化进 REST 响应。
 * </p>
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
public class AuditLog {

    /** 入库前为空，由仓储生成 */
    private final String id;
    private final String tenantId;
    private final String actorId;
    private final String actorName;
    /** 机器可读动作码，如 USER_PASSWORD_UPDATE */
    private final String action;
    /** 界面直接展示的中文文案 */
    private final String actionLabel;
    private final String targetType;
    private final String targetId;
    private final String detail;
    private final AuditResult result;
    private final String errorMessage;
    private final String ip;
    private final String traceId;
    private final LocalDateTime createdAt;
}
