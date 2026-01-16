package site.hexaarch.ecommerce.logistics.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;

/**
 * SKU管理服务，管理SKU信息，如创建、修改SKU。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class SKUManagementService {
    private final ProductRepository productRepository;

    /**
     * 给产品添加SKU
     */
    public Product addSkuToProduct(String productId, SKU sku) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.addSKU(sku);
        return productRepository.save(product);
    }
}
