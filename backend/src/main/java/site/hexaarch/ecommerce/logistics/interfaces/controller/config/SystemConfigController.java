package site.hexaarch.ecommerce.logistics.interfaces.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.query.config.SystemConfigView;
import site.hexaarch.ecommerce.logistics.application.service.config.SystemConfigApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.infrastructure.audit.AuditRequestContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

/**
 * 平台系统配置接口（平台运营职责，见 docs/spec/tenant-and-iam.md 角色职责表）。
 * <p>
 * 只给 {@code ROLE_PLATFORM_ADMIN}：配置里的会话超时与登录锁定阈值是平台级安全参数，
 * 不允许租户侧读写。保存属于安全参数变更，按 docs/tech/security-design.md §9 第 8 项强制审计。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/system-config")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "系统配置", description = "平台级系统配置读写")
public class SystemConfigController {

    private final SystemConfigApplicationService systemConfigApplicationService;
    private final AuditRequestContext auditRequestContext;

    public SystemConfigController(SystemConfigApplicationService systemConfigApplicationService,
                                  AuditRequestContext auditRequestContext) {
        this.systemConfigApplicationService = systemConfigApplicationService;
        this.auditRequestContext = auditRequestContext;
    }

    /**
     * 读取系统配置
     */
    @Operation(summary = "读取系统配置", description = "返回按分组聚合的平台配置，库中缺失的键回落到目录默认值")
    @GetMapping
    public ResponseEntity<Result<SystemConfigView>> getConfig() {
        return ResponseEntity.ok(Result.success(systemConfigApplicationService.current()));
    }

    /**
     * 保存系统配置（整体校验，单事务落库）
     */
    @Operation(summary = "保存系统配置", description = "全量校验后保存；任一项不合法则整批不生效")
    @PutMapping
    @Auditable(action = AuditActions.SYSTEM_CONFIG_UPDATE, label = "更新平台系统配置", targetType = "SystemConfig")
    public ResponseEntity<Result<SystemConfigView>> updateConfig(@RequestBody SystemConfigView config) {
        return ResponseEntity.ok(Result.success(
                systemConfigApplicationService.update(config, auditRequestContext.actorName())));
    }
}
