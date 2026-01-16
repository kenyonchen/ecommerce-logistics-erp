package site.hexaarch.ecommerce.logistics.domain.finance.repository;

import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.util.List;
import java.util.Optional;

/**
 * 财务交易仓储接口，负责财务交易的持久化和查询操作。
 *
 * @author kenyon
 */
public interface FinancialTransactionRepository {
    /**
     * 保存财务交易。
     *
     * @param financialTransaction 财务交易实例
     */
    void save(FinancialTransaction financialTransaction);

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例，若不存在则返回Optional.empty()
     */
    Optional<FinancialTransaction> findById(String transactionId);

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    List<FinancialTransaction> findByStatus(TransactionStatus status);

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    List<FinancialTransaction> findByType(TransactionType type);

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    List<FinancialTransaction> findByReference(String referenceId, String referenceType);
}
