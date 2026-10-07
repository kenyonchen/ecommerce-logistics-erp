package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 财务应用服务，负责协调领域对象完成财务相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class FinanceApplicationService {
    private final FinancialTransactionService financialTransactionService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造函数，注入财务交易领域服务。
     *
     * @param financialTransactionService 财务交易领域服务
     * @param domainEventPublisher        领域事件发布服务
     */
    public FinanceApplicationService(FinancialTransactionService financialTransactionService, DomainEventPublisher domainEventPublisher) {
        this.financialTransactionService = financialTransactionService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建财务交易（归属默认租户）。
     *
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @return 创建的财务交易
     */
    @Transactional
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        return createFinancialTransaction(type, amount, currency, referenceId, referenceType, description, null);
    }

    /**
     * 创建财务交易。
     *
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @param tenantId      租户ID（可空，空则归属默认租户）
     * @return 创建的财务交易
     */
    @Transactional
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description,
            String tenantId) {
        FinancialTransaction transaction = financialTransactionService.createFinancialTransaction(
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description,
                tenantId);
        // 发布财务交易聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例
     */
    public FinancialTransaction findFinancialTransactionById(String transactionId) {
        return financialTransactionService.findFinancialTransactionById(transactionId);
    }

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByStatus(TransactionStatus status) {
        return financialTransactionService.findFinancialTransactionsByStatus(status);
    }

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByType(TransactionType type) {
        return financialTransactionService.findFinancialTransactionsByType(type);
    }

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByReference(String referenceId, String referenceType) {
        return financialTransactionService.findFinancialTransactionsByReference(referenceId, referenceType);
    }

    /**
     * 按条件分页查询财务交易（按创建时间倒序）。
     *
     * @param tenantId      租户ID（可空）
     * @param type          交易类型（可空）
     * @param status        交易状态（可空）
     * @param referenceId   关联ID（可空）
     * @param referenceType 关联类型（可空）
     * @param from          创建时间起（可空）
     * @param to            创建时间止（可空）
     * @param page          页码，从0开始
     * @param size          每页条数
     * @return 分页结果
     */
    public PageResult<FinancialTransaction> searchFinancialTransactions(
            String tenantId,
            TransactionType type,
            TransactionStatus status,
            String referenceId,
            String referenceType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        return financialTransactionService.searchFinancialTransactions(tenantId, type, status, referenceId,
                referenceType, from, to, page, size);
    }

    /**
     * 完成财务交易。
     *
     * @param transactionId 交易ID
     * @return 完成后的财务交易
     */
    @Transactional
    public FinancialTransaction completeFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.completeFinancialTransaction(transactionId);
        // complete() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 失败财务交易。
     *
     * @param transactionId 交易ID
     * @return 失败后的财务交易
     */
    @Transactional
    public FinancialTransaction failFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.failFinancialTransaction(transactionId);
        // fail() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 取消财务交易。
     *
     * @param transactionId 交易ID
     * @return 取消后的财务交易
     */
    @Transactional
    public FinancialTransaction cancelFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.cancelFinancialTransaction(transactionId);
        // cancel() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }
}
