package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.product;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductQuery;

import java.util.ArrayList;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductJpaEntity;

/**
 * 商品列表查询条件构造。
 *
 * @author kenyon
 */
public final class ProductSpecification {

    private ProductSpecification() {
    }

    /**
     * 依据查询条件构造Specification，keyword 同时匹配商品名称、描述与SKU编码。
     */
    public static Specification<ProductJpaEntity> withQuery(ProductQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.tenantId())) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), query.tenantId()));
            }
            if (StringUtils.hasText(query.categoryName())) {
                predicates.add(criteriaBuilder.equal(root.get("categoryName"), query.categoryName()));
            }
            if (query.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), query.active()));
            }
            if (StringUtils.hasText(query.sourcePlatform())) {
                predicates.add(criteriaBuilder.equal(root.get("sourcePlatform"), query.sourcePlatform()));
            }
            if (StringUtils.hasText(query.skuCode())) {
                predicates.add(criteriaBuilder.equal(root.join("skus", JoinType.LEFT).get("skuCode"), query.skuCode()));
            }
            if (StringUtils.hasText(query.keyword())) {
                String pattern = "%" + query.keyword().trim().toLowerCase() + "%";
                Predicate nameLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern);
                Predicate descriptionLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern);
                Predicate skuLike = criteriaBuilder.like(
                        criteriaBuilder.lower(root.join("skus", JoinType.LEFT).get("skuCode")), pattern);
                predicates.add(criteriaBuilder.or(nameLike, descriptionLike, skuLike));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
