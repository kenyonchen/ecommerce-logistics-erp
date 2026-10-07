package site.hexaarch.ecommerce.logistics.domain.finance.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 财务交易领域服务的实现类，封装财务交易相关的业务逻辑。
 *
 * @author kenyon
 */
@Service
public class FinancialTransactionServiceImpl implements FinancialTransactionService {

    /**
     * 未显式指定租户时的默认归属租户
     */
    public static final String DEFAULT_TENANT_ID = "default-tenant";

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
     * @param tenantId      租户ID（为空时归属默认租户）
     * @return 创建的财务交易
     */
    @Override
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description,
            String tenantId) {
        var transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        var resolvedTenantId = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT_ID : tenantId.trim();
        // 聚合工厂签名为 create(transactionId, tenantId, ...)，参数顺序不可颠倒（历史缺陷回归防护）
        var financialTransaction = FinancialTransaction.create(
                transactionId,
                resolvedTenantId,
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description);
        // 落库前断言：交易ID与租户ID必须各归其位，防止主键错位
        if (!transactionId.equals(financialTransaction.getTransactionId())
                || !resolvedTenantId.equals(financialTransaction.getTenantId())) {
            throw new IllegalStateException("财务交易创建参数错位: transactionId="
                    + financialTransaction.getTransactionId() + ", tenantId=" + financialTransaction.getTenantId());
        }
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

    /**
     * 按条件分页查询财务交易。
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
    @Override
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
        return financialTransactionRepository.search(tenantId, type, status, referenceId, referenceType,
                from, to, page, size);
    }
}
