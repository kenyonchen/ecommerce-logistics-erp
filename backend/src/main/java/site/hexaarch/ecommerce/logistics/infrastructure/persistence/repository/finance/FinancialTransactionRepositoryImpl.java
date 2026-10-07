package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.finance;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinancialTransactionRepository;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinancialTransactionJpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.finance.FinancialTransactionSpecification;

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
    @Override
    public PageResult<FinancialTransaction> search(String tenantId, TransactionType type, TransactionStatus status,
                                                   String referenceId, String referenceType,
                                                   LocalDateTime from, LocalDateTime to, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = financialTransactionJpaRepository.findAll(FinancialTransactionSpecification
                .withQuery(tenantId, type, status, referenceId, referenceType, from, to), pageable);
        List<FinancialTransaction> transactions = result.getContent()
                .stream()
                .map(financialTransactionMapper::toDomainAggregate)
                .toList();
        return new PageResult<>(transactions, result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
