package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinancialTransactionJpaEntity;

import java.util.List;

/**
 * 财务交易JPA仓库接口，用于财务交易的数据库操作。
 *
 * @author kenyon
 */
@Repository
public interface FinancialTransactionJpaRepository extends JpaRepository<FinancialTransactionJpaEntity, String> {
    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    List<FinancialTransactionJpaEntity> findByStatus(String status);

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    List<FinancialTransactionJpaEntity> findByType(String type);

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    List<FinancialTransactionJpaEntity> findByReferenceIdAndReferenceType(String referenceId, String referenceType);
}
