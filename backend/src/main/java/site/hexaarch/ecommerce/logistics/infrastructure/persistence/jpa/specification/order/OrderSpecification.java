package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.order;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderQuery;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderStatusJpaEntity;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;

/**
 * 订单列表查询条件构造。
 *
 * @author kenyon
 */
public final class OrderSpecification {

    private OrderSpecification() {
    }

    /**
     * 依据查询条件构造Specification。
     * keyword 同时匹配订单号、客户名称、客户ID与平台订单号。
     */
    public static Specification<OrderJpaEntity> withQuery(OrderQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query == null) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
            if (hasText(query.tenantId())) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), query.tenantId().trim()));
            }
            if (hasText(query.orderNumber())) {
                predicates.add(criteriaBuilder.like(root.get("orderNumber"), "%" + query.orderNumber().trim() + "%"));
            }
            if (hasText(query.customerId())) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), query.customerId().trim()));
            }
            if (hasText(query.customerName())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")),
                        "%" + query.customerName().trim().toLowerCase() + "%"));
            }
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), toJpaStatus(query.status())));
            }
            if (hasText(query.platform())) {
                predicates.add(criteriaBuilder.equal(root.get("platform"), query.platform().trim()));
            }
            if (hasText(query.platformOrderId())) {
                predicates.add(criteriaBuilder.equal(root.get("platformOrderId"), query.platformOrderId().trim()));
            }
            if (hasText(query.keyword())) {
                String pattern = "%" + query.keyword().trim().toLowerCase() + "%";
                Predicate orderNumber = criteriaBuilder.like(criteriaBuilder.lower(root.get("orderNumber")), pattern);
                Predicate platformOrderId = criteriaBuilder.like(criteriaBuilder.lower(root.get("platformOrderId")), pattern);
                Predicate customerId = criteriaBuilder.like(criteriaBuilder.lower(root.get("customerId")), pattern);
                predicates.add(criteriaBuilder.or(orderNumber, platformOrderId, customerId));
            }
            if (query.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"),
                        OrderMapper.toEpochMillis(query.createdFrom())));
            }
            if (query.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"),
                        OrderMapper.toEpochMillis(query.createdTo())));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 领域状态枚举 -> JPA状态枚举（处理 CON_FIRMED 与 CONFIRMED 的命名差异）
     */
    private static OrderStatusJpaEntity toJpaStatus(OrderStatus status) {
        return switch (status) {
            case PENDING -> OrderStatusJpaEntity.PENDING;
            case PROCESSING -> OrderStatusJpaEntity.PROCESSING;
            case CON_FIRMED -> OrderStatusJpaEntity.CONFIRMED;
            case SHIPPED -> OrderStatusJpaEntity.SHIPPED;
            case COMPLETED -> OrderStatusJpaEntity.COMPLETED;
            case CANCELLED -> OrderStatusJpaEntity.CANCELLED;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
