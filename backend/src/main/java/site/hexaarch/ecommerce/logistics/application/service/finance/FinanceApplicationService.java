package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.util.List;

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
     * 创建财务交易。
     *
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @return 创建的财务交易
     */
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        FinancialTransaction transaction = financialTransactionService.createFinancialTransaction(
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description);
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
     * 完成财务交易。
     *
     * @param transactionId 交易ID
     * @return 完成后的财务交易
     */
    public FinancialTransaction completeFinancialTransaction(String transactionId) {
        return financialTransactionService.completeFinancialTransaction(transactionId);
    }

    /**
     * 失败财务交易。
     *
     * @param transactionId 交易ID
     * @return 失败后的财务交易
     */
    public FinancialTransaction failFinancialTransaction(String transactionId) {
        return financialTransactionService.failFinancialTransaction(transactionId);
    }

    /**
     * 取消财务交易。
     *
     * @param transactionId 交易ID
     * @return 取消后的财务交易
     */
    public FinancialTransaction cancelFinancialTransaction(String transactionId) {
        return financialTransactionService.cancelFinancialTransaction(transactionId);
    }
}
