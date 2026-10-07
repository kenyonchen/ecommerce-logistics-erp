package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductQuery;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product.ProductJpaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.product.ProductSpecification;

/**
 * 产品仓储实现类，提供产品聚合的持久化和访问功能。
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;

    @Override
    public Product save(Product product) {
        ProductJpaEntity productJpaEntity = productMapper.toJpaEntity(product);
        applyTimestamps(productJpaEntity);
        ProductJpaEntity savedEntity = productJpaRepository.save(productJpaEntity);
        return productMapper.toDomainAggregate(savedEntity);
    }

    @Override
    public List<Product> saveAll(Collection<Product> products) {
        List<ProductJpaEntity> entities = new ArrayList<>(products.size());
        for (Product product : products) {
            ProductJpaEntity entity = productMapper.toJpaEntity(product);
            applyTimestamps(entity);
            entities.add(entity);
        }
        List<Product> savedProducts = new ArrayList<>(entities.size());
        for (ProductJpaEntity savedEntity : productJpaRepository.saveAll(entities)) {
            savedProducts.add(productMapper.toDomainAggregate(savedEntity));
        }
        return savedProducts;
    }

    @Override
    public Optional<Product> findById(String id) {
        return productJpaRepository.findById(id).map(productMapper::toDomainAggregate);
    }

    @Override
    public Optional<Product> findBySkuCode(String skuCode) {
        return productJpaRepository.findBySkuCode(skuCode).map(productMapper::toDomainAggregate);
    }

    @Override
    public PageResult<Product> findAll(ProductQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), resolveSort(query.sort()));
        Specification<ProductJpaEntity> specification = ProductSpecification.withQuery(query);
        var page = productJpaRepository.findAll(specification, pageable);
        List<Product> products = new ArrayList<>(page.getNumberOfElements());
        for (ProductJpaEntity entity : page.getContent()) {
            products.add(productMapper.toDomainAggregate(entity));
        }
        return new PageResult<>(products, page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Override
    public Optional<Product> findBySourceProductId(String tenantId, String sourcePlatform, String sourceProductId) {
        if (tenantId == null || sourcePlatform == null || sourceProductId == null) {
            return Optional.empty();
        }
        return productJpaRepository
                .findByTenantIdAndSourcePlatformAndSourceProductId(tenantId, sourcePlatform, sourceProductId)
                .map(productMapper::toDomainAggregate);
    }

    @Override
    public boolean existsBySkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            return false;
        }
        return productJpaRepository.findBySkuCode(skuCode).isPresent();
    }

    @Override
    public void delete(String id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public int deleteAll(String tenantId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return (int) productJpaRepository.deleteByIdInAndTenantId(new ArrayList<>(ids), tenantId);
    }

    private void applyTimestamps(ProductJpaEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private Sort resolveSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String property = "createdAt";
        if (parts.length > 0 && !parts[0].isBlank()) {
            property = switch (parts[0].trim()) {
                case "name" -> "name";
                case "updatedAt" -> "updatedAt";
                default -> "createdAt";
            };
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }
        return Sort.by(direction, property);
    }
}
