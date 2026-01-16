package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinancialTransactionJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 财务交易仓储的实现类，使用JPA进行持久化操作。
 *
 * @author kenyon
 */
@Repository
public class FinancialTransactionRepositoryImpl implements FinancialTransactionRepository {
    private final FinancialTransactionJpaRepository financialTransactionJpaRepository;
    private final FinancialTransactionMapper financialTransactionMapper;

    /**
     * 构造函数，注入JPA仓库和映射器。
     *
     * @param financialTransactionJpaRepository 财务交易JPA仓库
     * @param financialTransactionMapper        财务交易映射器
     */
    public FinancialTransactionRepositoryImpl(
            FinancialTransactionJpaRepository financialTransactionJpaRepository,
            FinancialTransactionMapper financialTransactionMapper) {
        this.financialTransactionJpaRepository = financialTransactionJpaRepository;
        this.financialTransactionMapper = financialTransactionMapper;
    }

    /**
     * 保存财务交易。
     *
     * @param financialTransaction 财务交易实例
     */
    @Override
    public void save(FinancialTransaction financialTransaction) {
        var financialTransactionJpaEntity = financialTransactionMapper.toJpaEntity(financialTransaction);
        financialTransactionJpaRepository.save(financialTransactionJpaEntity);
    }

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例，若不存在则返回Optional.empty()
     */
    @Override
    public Optional<FinancialTransaction> findById(String transactionId) {
        return financialTransactionJpaRepository.findById(transactionId)
                .map(financialTransactionMapper::toDomainAggregate);
    }

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findByStatus(TransactionStatus status) {
        return financialTransactionJpaRepository.findByStatus(status.getCode())
                .stream()
                .map(financialTransactionMapper::toDomainAggregate)
                .toList();
    }

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findByType(TransactionType type) {
        return financialTransactionJpaRepository.findByType(type.getCode())
                .stream()
                .map(financialTransactionMapper::toDomainAggregate)
                .toList();
    }

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    @Override
    public List<FinancialTransaction> findByReference(String referenceId, String referenceType) {
        return financialTransactionJpaRepository.findByReferenceIdAndReferenceType(referenceId, referenceType)
                .stream()
                .map(financialTransactionMapper::toDomainAggregate)
                .toList();
    }
}
