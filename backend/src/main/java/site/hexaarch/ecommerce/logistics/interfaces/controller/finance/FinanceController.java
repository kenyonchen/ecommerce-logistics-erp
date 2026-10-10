package site.hexaarch.ecommerce.logistics.interfaces.controller.finance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.finance.CreateFinancialTransactionRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.finance.FinancialTransactionDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务控制器，处理财务相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/finance/transactions")
@Tag(name = "财务管理", description = "财务相关的API接口")
public class FinanceController {
    private final FinanceApplicationService financeApplicationService;
    private final FinancialTransactionMapper financialTransactionMapper;

    /**
     * 构造函数，注入财务应用服务。
     *
     * @param financeApplicationService  财务应用服务
     * @param financialTransactionMapper 财务交易映射器
     */
    public FinanceController(FinanceApplicationService financeApplicationService, FinancialTransactionMapper financialTransactionMapper) {
        this.financeApplicationService = financeApplicationService;
        this.financialTransactionMapper = financialTransactionMapper;
    }

    /**
     * 创建财务交易
     */
    @Operation(summary = "创建财务交易", description = "创建一个新的财务交易（JSON请求体）")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:write')")
    @Auditable(action = AuditActions.FINANCE_TRANSACTION_CREATE, label = "新建财务交易",
            targetType = "FinancialTransaction", targetIdResult = "transactionId")
    @PostMapping
    public Result<FinancialTransactionDto> createFinancialTransaction(@RequestBody CreateFinancialTransactionRequest request) {
        try {
            var financialTransaction = financeApplicationService.createFinancialTransaction(
                    request.getType(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReferenceId(),
                    request.getReferenceType(),
                    request.getDescription(),
                    // 必须过 resolveTenant：本端点此前直传 request.getTenantId()，而读侧走 resolveTenant，
                    // 缺省 tenantId 时两边口径不一致——金额记录会落进谁也看不见的 default-tenant 桶（ISS-118）。
                    resolveTenant(request.getTenantId()));
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (BusinessException e) {
            // ISS-136：业务拒绝必须原样交给 GlobalExceptionHandler（HTTP 400 + 业务码）。
            // 被下面的兜底 catch 复述成 code 500 的字符串消息时，前端按 code 查文案表只会显示
            // 「系统内部错误，请稍后重试」，已经拿到手的原因就此消失。
            throw e;
        } catch (Exception e) {
            return Result.error("创建财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询财务交易列表
     */
    @Operation(summary = "分页查询财务交易", description = "按类型/状态/关联单据/创建时间区间过滤，按创建时间倒序")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:read')")
    @GetMapping
    public Result<PageResult<FinancialTransactionDto>> searchFinancialTransactions(
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "交易类型") @RequestParam(required = false) TransactionType type,
            @Parameter(description = "交易状态") @RequestParam(required = false) TransactionStatus status,
            @Parameter(description = "关联ID") @RequestParam(required = false) String referenceId,
            @Parameter(description = "关联类型") @RequestParam(required = false) String referenceType,
            @Parameter(description = "创建时间起（ISO格式）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "创建时间止（ISO格式）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "租户ID（可空，缺省取当前登录租户）") @RequestParam(required = false) String tenantId) {
        PageResult<FinancialTransaction> transactions = financeApplicationService.searchFinancialTransactions(
                resolveTenant(tenantId), type, status, referenceId, referenceType, from, to,
                page == null ? 0 : page, size == null ? 20 : size);
        List<FinancialTransactionDto> content = transactions.getContent().stream()
                .map(financialTransactionMapper::toDto)
                .toList();
        return Result.success(new PageResult<>(content, transactions.getTotal(), transactions.getPage(), transactions.getSize()));
    }

    /**
     * 租户口径统一（ISS-050/051）：入参优先，缺省回退 JWT 上下文租户，再回退 default-tenant。
     */
    private String resolveTenant(String tenantId) {
        String requested = TenantContext.normalizeRequested(tenantId);
        if (requested != null) {
            return requested;
        }
        if (TenantContext.hasTenantId()) {
            return TenantContext.getTenantId();
        }
        return "default-tenant";
    }

    /**
     * 根据交易ID查找财务交易
     */
    @Operation(summary = "根据交易ID查找财务交易", description = "根据交易ID查找财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:read')")
    @GetMapping("/{transactionId}")
    public Result<FinancialTransactionDto> findFinancialTransactionById(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.findFinancialTransactionById(transactionId);
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据状态查找财务交易
     */
    @Operation(summary = "根据状态查找财务交易", description = "根据交易状态查找财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:read')")
    @GetMapping("/by-status")
    public Result<List<FinancialTransactionDto>> findFinancialTransactionsByStatus(@Parameter(description = "交易状态") @RequestParam TransactionStatus status) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByStatus(status);
            return Result.success(financialTransactions.stream()
                    .map(financialTransactionMapper::toDto)
                    .toList());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据类型查找财务交易
     */
    @Operation(summary = "根据类型查找财务交易", description = "根据交易类型查找财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:read')")
    @GetMapping("/by-type")
    public Result<List<FinancialTransactionDto>> findFinancialTransactionsByType(@Parameter(description = "交易类型") @RequestParam TransactionType type) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByType(type);
            return Result.success(financialTransactions.stream()
                    .map(financialTransactionMapper::toDto)
                    .toList());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据关联ID和类型查找财务交易
     */
    @Operation(summary = "根据关联ID和类型查找财务交易", description = "根据关联ID和类型查找财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:read')")
    @GetMapping("/by-reference")
    public Result<List<FinancialTransactionDto>> findFinancialTransactionsByReference(
            @Parameter(description = "关联ID") @RequestParam String referenceId,
            @Parameter(description = "关联类型") @RequestParam String referenceType) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByReference(referenceId, referenceType);
            return Result.success(financialTransactions.stream()
                    .map(financialTransactionMapper::toDto)
                    .toList());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 完成财务交易
     */
    @Operation(summary = "根据交易ID完成财务交易", description = "根据交易ID完成财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:write')")
    @Auditable(action = AuditActions.FINANCE_TRANSACTION_COMPLETE, label = "标记财务交易为已完成", targetType = "FinancialTransaction")
    @PutMapping("/{transactionId}/complete")
    public Result<FinancialTransactionDto> completeFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.completeFinancialTransaction(transactionId);
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("完成财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 失败财务交易
     */
    @Operation(summary = "根据交易ID标记财务交易为失败", description = "根据交易ID标记财务交易为失败")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:write')")
    @Auditable(action = AuditActions.FINANCE_TRANSACTION_FAIL, label = "标记财务交易为失败", targetType = "FinancialTransaction")
    @PutMapping("/{transactionId}/fail")
    public Result<FinancialTransactionDto> failFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.failFinancialTransaction(transactionId);
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("标记财务交易为失败: " + e.getMessage());
        }
    }

    /**
     * 取消财务交易
     */
    @Operation(summary = "取消财务交易", description = "根据交易ID取消财务交易")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:write')")
    @Auditable(action = AuditActions.FINANCE_TRANSACTION_CANCEL, label = "取消财务交易", targetType = "FinancialTransaction")
    @PutMapping("/{transactionId}/cancel")
    public Result<FinancialTransactionDto> cancelFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.cancelFinancialTransaction(transactionId);
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("取消财务交易失败: " + e.getMessage());
        }
    }
}
