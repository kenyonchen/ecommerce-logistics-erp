package site.hexaarch.ecommerce.logistics.domain.finance.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 财务交易领域服务的实现类，封装财务交易相关的业务逻辑。
 *
 * @author kenyon
 */
@Service
public class FinancialTransactionServiceImpl implements FinancialTransactionService {
    private final FinancialTransactionRepository financialTransactionRepository;

    /**
     * 构造函数，注入财务交易仓储。
     *
     * @param financialTransactionRepository 财务交易仓储
     */
    public FinancialTransactionServiceImpl(FinancialTransactionRepository financialTransactionRepository) {
        this.financialTransactionRepository = financialTransactionRepository;
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
    @Override
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        var transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        var financialTransaction = FinancialTransaction.create(
                "default-tenant",
                transactionId,
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description);
        financialTransactionRepository.save(financialTransaction);
        return financialTransaction;
    }

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例
     */
    @Override
    public FinancialTransaction findFinancialTransactionById(String transactionId) {
        return financialTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("财务交易不存在: " + transactionId));
    }

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findFinancialTransactionsByStatus(TransactionStatus status) {
        return financialTransactionRepository.findByStatus(status);
    }

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findFinancialTransactionsByType(TransactionType type) {
        return financialTransactionRepository.findByType(type);
    }

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findFinancialTransactionsByReference(String referenceId, String referenceType) {
        return financialTransactionRepository.findByReference(referenceId, referenceType);
    }

    /**
     * 完成财务交易。
     *
     * @param transactionId 交易ID
     * @return 完成后的财务交易
     */
    @Override
    public FinancialTransaction completeFinancialTransaction(String transactionId) {
        var financialTransaction = findFinancialTransactionById(transactionId);
        financialTransaction.complete();
        financialTransactionRepository.save(financialTransaction);
        return financialTransaction;
    }

    /**
     * 失败财务交易。
     *
     * @param transactionId 交易ID
     * @return 失败后的财务交易
     */
    @Override
    public FinancialTransaction failFinancialTransaction(String transactionId) {
        var financialTransaction = findFinancialTransactionById(transactionId);
        financialTransaction.fail();
        financialTransactionRepository.save(financialTransaction);
        return financialTransaction;
    }

    /**
     * 取消财务交易。
     *
     * @param transactionId 交易ID
     * @return 取消后的财务交易
     */
    @Override
    public FinancialTransaction cancelFinancialTransaction(String transactionId) {
        var financialTransaction = findFinancialTransactionById(transactionId);
        financialTransaction.cancel();
        financialTransactionRepository.save(financialTransaction);
        return financialTransaction;
    }
}
