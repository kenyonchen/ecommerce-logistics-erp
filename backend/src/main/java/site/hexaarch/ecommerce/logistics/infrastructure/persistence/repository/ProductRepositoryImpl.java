package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.ProductMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.ProductJpaRepository;

import java.util.Optional;

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
        var productJpaEntity = productMapper.toJpaEntity(product);
        var savedEntity = productJpaRepository.save(productJpaEntity);
        return productMapper.toDomainAggregate(savedEntity);
    }

    @Override
    public Optional<Product> findById(String id) {
        var productJpaEntityOpt = productJpaRepository.findById(id);
        return productJpaEntityOpt.map(productMapper::toDomainAggregate);
    }

    @Override
    public Optional<Product> findBySkuCode(String skuCode) {
        var productJpaEntityOpt = productJpaRepository.findBySkuCode(skuCode);
        return productJpaEntityOpt.map(productMapper::toDomainAggregate);
    }

    @Override
    public void delete(String id) {
        productJpaRepository.deleteById(id);
    }
}