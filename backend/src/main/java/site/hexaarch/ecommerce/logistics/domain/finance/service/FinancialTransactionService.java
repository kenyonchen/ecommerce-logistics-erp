package site.hexaarch.ecommerce.logistics.domain.finance.service;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务交易领域服务，封装财务交易相关的业务逻辑。
 *
 * @author kenyon
 */
public interface FinancialTransactionService {
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
    default FinancialTransaction createFinancialTransaction(
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
     * @param tenantId      租户ID（为空时归属默认租户）
     * @return 创建的财务交易
     */
    FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description,
            String tenantId);

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
    PageResult<FinancialTransaction> searchFinancialTransactions(
            String tenantId,
            TransactionType type,
            TransactionStatus status,
            String referenceId,
            String referenceType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size);

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
