package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.customer;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerJpaEntity;

/**
 * 客户列表查询条件构造。
 *
 * @author kenyon
 */
public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    /**
     * 依据查询条件构造Specification，keyword 同时匹配客户ID、名称、邮箱与电话。
     */
    public static Specification<CustomerJpaEntity> withQuery(CustomerQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.tenantId())) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), query.tenantId()));
            }
            if (StringUtils.hasText(query.customerId())) {
                predicates.add(criteriaBuilder.equal(root.get("id"), query.customerId().trim()));
            }
            if (StringUtils.hasText(query.customerName())) {
                predicates.add(likeIgnoreCase(criteriaBuilder, root, "name", query.customerName()));
            }
            if (StringUtils.hasText(query.email())) {
                predicates.add(likeIgnoreCase(criteriaBuilder, root, "email", query.email()));
            }
            if (StringUtils.hasText(query.phone())) {
                predicates.add(likeIgnoreCase(criteriaBuilder, root, "phone", query.phone()));
            }
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status()));
            } else {
                // 未显式指定状态时排除已软删除客户
                predicates.add(criteriaBuilder.notEqual(root.get("status"),
                        site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus.DELETED));
            }
            if (StringUtils.hasText(query.sourcePlatform())) {
                predicates.add(criteriaBuilder.equal(root.get("sourcePlatform"), query.sourcePlatform()));
            }
            if (query.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            }
            if (query.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), query.createdTo()));
            }
            if (StringUtils.hasText(query.keyword())) {
                String pattern = "%" + query.keyword().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("id")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate likeIgnoreCase(jakarta.persistence.criteria.CriteriaBuilder cb,
                                            jakarta.persistence.criteria.Root<CustomerJpaEntity> root,
                                            String property, String value) {
        return cb.like(cb.lower(root.get(property)), "%" + value.trim().toLowerCase() + "%");
    }
}
