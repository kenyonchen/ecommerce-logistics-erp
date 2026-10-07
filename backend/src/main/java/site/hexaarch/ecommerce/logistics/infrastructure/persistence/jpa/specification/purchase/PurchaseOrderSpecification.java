package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.purchase;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 采购单列表查询条件构造。
 *
 * @author kenyon
 */
public final class PurchaseOrderSpecification {

    private PurchaseOrderSpecification() {
    }

    /**
     * 依据查询条件构造Specification。
     * tenantId 为空时不加租户条件，但仓储层保证服务侧一定带上租户。
     */
    public static Specification<PurchaseOrderJpaEntity> withQuery(PurchaseQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query == null) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
            if (hasText(query.tenantId())) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), query.tenantId().trim()));
            }
            if (hasText(query.purchaseOrderId())) {
                predicates.add(criteriaBuilder.like(root.get("purchaseOrderId"), "%" + query.purchaseOrderId().trim() + "%"));
            }
            if (hasText(query.supplierId())) {
                predicates.add(criteriaBuilder.equal(root.get("supplierId"), query.supplierId().trim()));
            }
            if (hasText(query.supplierName())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("supplierName")),
                        "%" + query.supplierName().trim().toLowerCase() + "%"));
            }
            if (hasText(query.warehouseId())) {
                predicates.add(criteriaBuilder.equal(root.get("warehouseId"), query.warehouseId().trim()));
            }
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status().getCode()));
            }
            if (query.minTotalAmount() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("totalAmount"), query.minTotalAmount()));
            }
            if (query.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            }
            if (query.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), query.createdTo()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
