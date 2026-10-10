package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;

import java.time.LocalDateTime;

/**
 * 审计日志JPA实体，对应 {@code audit_logs} 表。
 * <p>
 * 表名与列名按 docs/tech/security-design.md §7 的审计目标形态设计；
 * 项目采用 Hibernate {@code ddl-auto: update}（ADR-0002），新表由启动时自动建，
 * 后续改列宽需要手工 {@code ALTER TABLE}。
 * </p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "idx_audit_logs_actor_result_created", columnList = "actor_name,result,created_at")
})
@Getter
@Setter
public class AuditLogJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    /** 平台级操作（如登录失败、平台配置变更）可为空 */
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_name", length = 64)
    private String actorName;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "action_label", length = 128)
    private String actionLabel;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "detail", length = 512)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private AuditResult result;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "trace_id", length = 32)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLogJpaEntity() {
        this.createdAt = LocalDateTime.now();
        this.result = AuditResult.SUCCESS;
    }
}
