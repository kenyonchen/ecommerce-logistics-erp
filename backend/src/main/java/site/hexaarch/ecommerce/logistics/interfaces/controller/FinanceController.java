package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapper;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.finance.FinancialTransactionDto;

import java.math.BigDecimal;
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
    @Operation(summary = "创建财务交易", description = "创建一个新的财务交易")
    @PostMapping
    public Result<FinancialTransactionDto> createFinancialTransaction(
            @Parameter(description = "交易类型") @RequestParam TransactionType type,
            @Parameter(description = "金额") @RequestParam BigDecimal amount,
            @Parameter(description = "币种") @RequestParam String currency,
            @Parameter(description = "关联ID") @RequestParam String referenceId,
            @Parameter(description = "关联类型") @RequestParam String referenceType,
            @Parameter(description = "交易描述") @RequestParam(required = false) String description) {
        try {
            var financialTransaction = financeApplicationService.createFinancialTransaction(
                    type,
                    amount,
                    currency,
                    referenceId,
                    referenceType,
                    description);
            return Result.success(financialTransactionMapper.toDto(financialTransaction));
        } catch (Exception e) {
            return Result.error("创建财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据交易ID查找财务交易
     */
    @Operation(summary = "根据交易ID查找财务交易", description = "根据交易ID查找财务交易")
    @GetMapping("/{transactionId}")
    public Result<FinancialTransaction> findFinancialTransactionById(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.findFinancialTransactionById(transactionId);
            return Result.success(financialTransaction);
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据状态查找财务交易
     */
    @Operation(summary = "根据状态查找财务交易", description = "根据交易状态查找财务交易")
    @GetMapping("/by-status")
    public Result<List<FinancialTransaction>> findFinancialTransactionsByStatus(@Parameter(description = "交易状态") @RequestParam TransactionStatus status) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByStatus(status);
            return Result.success(financialTransactions);
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据类型查找财务交易
     */
    @Operation(summary = "根据类型查找财务交易", description = "根据交易类型查找财务交易")
    @GetMapping("/by-type")
    public Result<List<FinancialTransaction>> findFinancialTransactionsByType(@Parameter(description = "交易类型") @RequestParam TransactionType type) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByType(type);
            return Result.success(financialTransactions);
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 根据关联ID和类型查找财务交易
     */
    @Operation(summary = "根据关联ID和类型查找财务交易", description = "根据关联ID和类型查找财务交易")
    @GetMapping("/by-reference")
    public Result<List<FinancialTransaction>> findFinancialTransactionsByReference(
            @Parameter(description = "关联ID") @RequestParam String referenceId,
            @Parameter(description = "关联类型") @RequestParam String referenceType) {
        try {
            var financialTransactions = financeApplicationService.findFinancialTransactionsByReference(referenceId, referenceType);
            return Result.success(financialTransactions);
        } catch (Exception e) {
            return Result.error("查找财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 完成财务交易
     */
    @Operation(summary = "根据交易ID完成财务交易", description = "根据交易ID完成财务交易")
    @PutMapping("/{transactionId}/complete")
    public Result<FinancialTransaction> completeFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.completeFinancialTransaction(transactionId);
            return Result.success(financialTransaction);
        } catch (Exception e) {
            return Result.error("完成财务交易失败: " + e.getMessage());
        }
    }

    /**
     * 失败财务交易
     */
    @Operation(summary = "根据交易ID标记财务交易为失败", description = "根据交易ID标记财务交易为失败")
    @PutMapping("/{transactionId}/fail")
    public Result<FinancialTransaction> failFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.failFinancialTransaction(transactionId);
            return Result.success(financialTransaction);
        } catch (Exception e) {
            return Result.error("标记财务交易为失败: " + e.getMessage());
        }
    }

    /**
     * 取消财务交易
     */
    @Operation(summary = "取消财务交易", description = "根据交易ID取消财务交易")
    @PutMapping("/{transactionId}/cancel")
    public Result<FinancialTransaction> cancelFinancialTransaction(@Parameter(description = "交易ID") @PathVariable String transactionId) {
        try {
            var financialTransaction = financeApplicationService.cancelFinancialTransaction(transactionId);
            return Result.success(financialTransaction);
        } catch (Exception e) {
            return Result.error("取消财务交易失败: " + e.getMessage());
        }
    }
}
