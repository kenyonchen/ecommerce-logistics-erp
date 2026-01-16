package site.hexaarch.ecommerce.logistics.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;

/**
 * 产品管理服务，管理产品信息，如创建、修改产品。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class ProductManagementService {
    private final ProductRepository productRepository;

    /**
     * 创建产品
     */
    public Product createProduct(String tenantId, String name, String description, ProductCategory category) {
        var product = Product.create(tenantId, name, description, category);
        return productRepository.save(product);
    }

    /**
     * 更新产品信息
     */
    public Product updateProduct(String productId, String name, String description) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.updateInfo(name, description);
        return productRepository.save(product);
    }

    /**
     * 激活产品
     */
    public Product activateProduct(String productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.activate();
        return productRepository.save(product);
    }

    /**
     * 停用产品
     */
    public Product deactivateProduct(String productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.deactivate();
        return productRepository.save(product);
    }
}
