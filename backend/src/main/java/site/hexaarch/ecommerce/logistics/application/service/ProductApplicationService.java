package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductCategoryService;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductManagementService;
import site.hexaarch.ecommerce.logistics.domain.product.service.SKUManagementService;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.UUID;

/**
 * 产品应用服务，协调领域对象完成产品相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class ProductApplicationService {
    private final ProductRepository productRepository;
    private final ProductManagementService productManagementService;
    private final SKUManagementService skuManagementService;
    private final ProductCategoryService productCategoryService;
    private final DomainEventPublisher domainEventPublisher;

    // 手动添加构造函数，避免Lombok注解问题
    public ProductApplicationService(ProductRepository productRepository, ProductManagementService productManagementService, SKUManagementService skuManagementService, ProductCategoryService productCategoryService, DomainEventPublisher domainEventPublisher) {
        this.productRepository = productRepository;
        this.productManagementService = productManagementService;
        this.skuManagementService = skuManagementService;
        this.productCategoryService = productCategoryService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建产品分类
     */
    public ProductCategory createCategory(String name, String description, ProductCategory parentCategory) {
        return productCategoryService.createCategory(name, description, parentCategory);
    }

    /**
     * 创建产品
     */
    public Product createProduct(String tenantId, String name, String description, ProductCategory category) {
        Product product = productManagementService.createProduct(tenantId, name, description, category);
        // 发布产品聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(product);
        return product;
    }

    /**
     * 更新产品信息
     */
    public Product updateProduct(String productId, String name, String description) {
        return productManagementService.updateProduct(productId, name, description);
    }

    /**
     * 激活产品
     */
    public Product activateProduct(String productId) {
        return productManagementService.activateProduct(productId);
    }

    /**
     * 停用产品
     */
    public Product deactivateProduct(String productId) {
        return productManagementService.deactivateProduct(productId);
    }

    /**
     * 添加SKU到产品
     */
    public Product addSkuToProduct(String productId, String skuCode, ProductPrice price,
                                   Integer weightInGrams, Integer volumeInCubicCentimeters) {
        var sku = SKU.builder()
                .id(UUID.randomUUID().toString())
                .skuCode(skuCode)
                .productId(productId)
                .price(price)
                .weightInGrams(weightInGrams)
                .volumeInCubicCentimeters(volumeInCubicCentimeters)
                .build();

        return skuManagementService.addSkuToProduct(productId, sku);
    }

    /**
     * 根据ID查找产品
     */
    public Product findProductById(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    /**
     * 根据SKU编码查找产品
     */
    public Product findProductBySkuCode(String skuCode) {
        return productRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
}
