package site.hexaarch.ecommerce.logistics.interfaces.controller.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.audit.AuditApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 审计日志查询接口（平台监控能力，见 docs/spec/tenant-and-iam.md 平台管理员职责表）。
 * <p>
 * 门槛只给 {@code ROLE_PLATFORM_ADMIN}：审计记录里有操作人、IP、对象 ID 与失败原因，
 * 属于平台侧取证数据，不下沉为租户级权限点。服务层另有 {@code TenantAccessGuard} 收敛，
 * 即便后续放开权限点也不会漏出跨租户记录。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "审计日志", description = "敏感操作留痕的查询接口")
public class AuditLogController {

    private final AuditApplicationService auditApplicationService;

    public AuditLogController(AuditApplicationService auditApplicationService) {
        this.auditApplicationService = auditApplicationService;
    }

    /**
     * 分页查询审计记录
     */
    @Operation(summary = "分页查询审计日志", description = "按租户/动作码/结果过滤，按时间倒序返回")
    @GetMapping
    public ResponseEntity<Result<PageResult<AuditLog>>> search(
            @Parameter(description = "租户ID，平台管理员留空表示全平台") @RequestParam(required = false) String tenantId,
            @Parameter(description = "动作码，如 USER_PASSWORD_UPDATE") @RequestParam(required = false) String action,
            @Parameter(description = "结果：SUCCESS / FAILURE") @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Result.success(
                auditApplicationService.search(tenantId, action, result, page, size)));
    }

    /**
     * 最近活动（平台看板用）
     */
    @Operation(summary = "最近审计记录", description = "返回最近 limit 条敏感操作，上限 50 条")
    @GetMapping("/recent")
    public ResponseEntity<Result<List<AuditLog>>> recent(
            @Parameter(description = "租户ID，平台管理员留空表示全平台") @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(Result.success(auditApplicationService.findRecent(tenantId, limit)));
    }
}
