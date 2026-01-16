package site.hexaarch.ecommerce.logistics.domain.product.repository;

import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;

import java.util.Optional;

/**
 * 产品仓储接口，负责产品聚合的持久化和访问。
 *
 * @author kenyon
 */
public interface ProductRepository {
    /**
     * 保存产品聚合
     */
    Product save(Product product);

    /**
     * 根据ID查找产品
     */
    Optional<Product> findById(String id);

    /**
     * 根据SKU编码查找产品
     */
    Optional<Product> findBySkuCode(String skuCode);

    /**
     * 删除产品
     */
    void delete(String id);
}
