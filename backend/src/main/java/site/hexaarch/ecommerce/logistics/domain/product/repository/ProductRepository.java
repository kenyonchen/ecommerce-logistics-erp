package site.hexaarch.ecommerce.logistics.domain.product.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;

import java.util.Collection;
import java.util.List;
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
     * 批量保存产品聚合，用于第三方平台商品导入
     */
    List<Product> saveAll(Collection<Product> products);

    /**
     * 根据ID查找产品
     */
    Optional<Product> findById(String id);

    /**
     * 根据SKU编码查找产品
     */
    Optional<Product> findBySkuCode(String skuCode);

    /**
     * 分页查询产品
     */
    PageResult<Product> findAll(ProductQuery query);

    /**
     * 根据第三方平台的商品ID查找产品，用于重复导入时的幂等更新
     */
    Optional<Product> findBySourceProductId(String tenantId, String sourcePlatform, String sourceProductId);

    /**
     * SKU编码是否已存在
     */
    boolean existsBySkuCode(String skuCode);

    /**
     * 删除产品
     */
    void delete(String id);

    /**
     * 批量删除指定租户下的产品
     *
     * @return 实际删除的数量
     */
    int deleteAll(String tenantId, Collection<String> ids);
}
