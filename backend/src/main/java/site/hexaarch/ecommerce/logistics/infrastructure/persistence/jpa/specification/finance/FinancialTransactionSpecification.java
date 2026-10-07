package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.finance;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinancialTransactionJpaEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务交易列表查询条件构造。
 * <p>
 * 注意：type/status 落库形态是枚举小写 code（income/pending 等，见 FinancialTransactionMapper），
 * 因此条件构造时先用枚举的 getCode() 转换。
 * </p>
 *
 * @author kenyon
 */
public final class FinancialTransactionSpecification {

    private FinancialTransactionSpecification() {
    }

    /**
     * 依据查询条件构造Specification（空条件不参与过滤）。
     */
    public static Specification<FinancialTransactionJpaEntity> withQuery(
            String tenantId, TransactionType type, TransactionStatus status,
            String referenceId, String referenceType, LocalDateTime from, LocalDateTime to) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(tenantId)) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId.trim()));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type.getCode()));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.getCode()));
            }
            if (hasText(referenceId)) {
                predicates.add(criteriaBuilder.equal(root.get("referenceId"), referenceId.trim()));
            }
            if (hasText(referenceType)) {
                predicates.add(criteriaBuilder.equal(root.get("referenceType"), referenceType.trim()));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
