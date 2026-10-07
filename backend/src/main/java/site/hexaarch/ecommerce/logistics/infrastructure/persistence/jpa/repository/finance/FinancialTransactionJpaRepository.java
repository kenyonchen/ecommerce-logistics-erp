package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinancialTransactionJpaEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务交易JPA仓库接口，用于财务交易的数据库操作。
 *
 * @author kenyon
 */
@Repository
public interface FinancialTransactionJpaRepository extends JpaRepository<FinancialTransactionJpaEntity, String>,
        JpaSpecificationExecutor<FinancialTransactionJpaEntity> {
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

    /**
     * 按租户、交易类型与状态汇总金额（首页概览的总收入口径）。
     * <p>
     * type / status 落库值为领域枚举的 code（小写，如 {@code income} / {@code success}），
     * 见 FinancialTransactionMapper。汇总结果可能为 null（无匹配数据），由调用方兜底为 0。
     * </p>
     *
     * @param tenantId 租户ID
     * @param type     交易类型 code
     * @param status   交易状态 code
     * @return 金额合计，无数据时为 null
     */
    @Query("SELECT SUM(f.amount) FROM FinancialTransactionJpaEntity f "
            + "WHERE f.tenantId = :tenantId AND f.type = :type AND f.status = :status")
    BigDecimal sumAmountByTenantIdAndTypeAndStatus(@Param("tenantId") String tenantId,
                                                  @Param("type") String type,
                                                  @Param("status") String status);
}
