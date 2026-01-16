package site.hexaarch.ecommerce.logistics.domain.finance.service;

import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务交易领域服务，封装财务交易相关的业务逻辑。
 *
 * @author kenyon
 */
public interface FinancialTransactionService {
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
    FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description);

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例
     */
    FinancialTransaction findFinancialTransactionById(String transactionId);

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    List<FinancialTransaction> findFinancialTransactionsByStatus(TransactionStatus status);

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    List<FinancialTransaction> findFinancialTransactionsByType(TransactionType type);

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    List<FinancialTransaction> findFinancialTransactionsByReference(String referenceId, String referenceType);

    /**
     * 完成财务交易。
     *
     * @param transactionId 交易ID
     * @return 完成后的财务交易
     */
    FinancialTransaction completeFinancialTransaction(String transactionId);

    /**
     * 失败财务交易。
     *
     * @param transactionId 交易ID
     * @return 失败后的财务交易
     */
    FinancialTransaction failFinancialTransaction(String transactionId);

    /**
     * 取消财务交易。
     *
     * @param transactionId 交易ID
     * @return 取消后的财务交易
     */
    FinancialTransaction cancelFinancialTransaction(String transactionId);
}
