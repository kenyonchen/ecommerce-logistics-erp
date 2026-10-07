package site.hexaarch.ecommerce.logistics.application.service.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateProductCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateSkuCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.ProductAttributeCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.UpdateProductCommand;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductQuery;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductCategoryService;
import site.hexaarch.ecommerce.logistics.domain.product.service.ProductManagementService;
import site.hexaarch.ecommerce.logistics.domain.product.service.SKUManagementService;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductAttribute;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 产品应用服务，协调领域对象完成产品相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class ProductApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";
    public static final String DEFAULT_CURRENCY = "CNY";

    private final ProductRepository productRepository;
    private final ProductManagementService productManagementService;
    private final SKUManagementService skuManagementService;
    private final ProductCategoryService productCategoryService;
    private final DomainEventPublisher domainEventPublisher;

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
     * 创建产品（兼容接口）
     */
    public Product createProduct(String tenantId, String name, String description, ProductCategory category) {
        Product product = productManagementService.createProduct(tenantId, name, description, category);
        // 发布产品聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(product);
        return product;
    }

    /**
     * 创建产品（含SKU与第三方来源标识）
     */
    @Transactional
    public Product createProduct(CreateProductCommand command) {
        String tenantId = resolveTenant(command.tenantId());
        String name = normalize(command.name());
        validateName(name);

        ProductCategory category = resolveCategory(command.categoryId(), command.categoryName());
        LocalDateTime now = LocalDateTime.now();

        Product product = Product.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .description(command.description())
                .category(category)
                .skus(new ArrayList<>())
                .active(command.active() == null || command.active())
                .domainEvents(new ArrayList<>())
                .sourcePlatform(command.sourcePlatform())
                .sourceProductId(command.sourceProductId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        product.getDomainEvents().add(ProductCreatedEvent.builder()
                .productId(product.getId())
                .tenantId(product.getTenantId())
                .productName(product.getName())
                .timestamp(System.currentTimeMillis())
                .build());

        List<CreateSkuCommand> skus = command.skus() == null ? List.of() : command.skus();
        Set<String> skuCodes = new HashSet<>();
        for (CreateSkuCommand skuCommand : skus) {
            String skuCode = normalize(skuCommand.skuCode());
            validateSku(skuCode, skuCommand.sellingPrice());
            if (!skuCodes.add(skuCode)) {
                throw new ValidationException("商品SKU编码重复: " + skuCode);
            }
            if (productRepository.existsBySkuCode(skuCode)) {
                throw new ValidationException("SKU编码已存在: " + skuCode);
            }
            product.addSKU(buildSku(product.getId(), skuCode, skuCommand));
        }

        Product saved = productRepository.save(product);
        domainEventPublisher.publishEventsFrom(product);
        product.clearDomainEvents();
        return saved;
    }

    /**
     * 更新产品信息
     */
    public Product updateProduct(String productId, String name, String description) {
        return productManagementService.updateProduct(productId, name, description);
    }

    /**
     * 更新产品（名称、描述、分类、启用状态、SKU整体覆盖）
     */
    @Transactional
    public Product updateProduct(String productId, UpdateProductCommand command) {
        Product product = requireProduct(productId);

        if (StringUtils.hasText(command.name()) || StringUtils.hasText(command.description())) {
            String newName = StringUtils.hasText(command.name()) ? normalize(command.name()) : product.getName();
            String newDescription = StringUtils.hasText(command.description())
                    ? command.description()
                    : product.getDescription();
            product.updateInfo(newName, newDescription);
        }

        if (StringUtils.hasText(command.categoryName()) || StringUtils.hasText(command.categoryId())) {
            product.updateCategory(resolveCategory(command.categoryId(), command.categoryName()));
        }

        if (command.active() != null) {
            if (command.active()) {
                product.activate();
            } else {
                product.deactivate();
            }
        }

        if (command.skus() != null) {
            replaceSkus(product, command.skus());
        }

        product.touch();
        Product saved = productRepository.save(product);
        domainEventPublisher.publishEventsFrom(product);
        product.clearDomainEvents();
        return saved;
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
     * 批量启用/停用产品
     *
     * @return 实际处理的数量
     */
    @Transactional
    public int batchChangeActive(List<String> productIds, String tenantId, boolean active) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String productId : productIds) {
            Product product = requireProduct(productId, tenantId);
            if (active) {
                product.activate();
            } else {
                product.deactivate();
            }
            product.touch();
            productRepository.save(product);
            count++;
        }
        return count;
    }

    /**
     * 删除产品
     */
    @Transactional
    public void deleteProduct(String productId, String tenantId) {
        Product product = requireProduct(productId, tenantId);
        productRepository.delete(product.getId());
    }

    /**
     * 批量删除产品
     *
     * @return 实际删除的数量
     */
    @Transactional
    public int batchDeleteProducts(List<String> productIds, String tenantId) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        return productRepository.deleteAll(resolveTenant(tenantId), productIds);
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
     * 添加SKU到产品（含名称、成本价、币种、属性）
     */
    @Transactional
    public Product addSkuToProduct(String productId, CreateSkuCommand command) {
        Product product = requireProduct(productId);
        String skuCode = normalize(command.skuCode());
        validateSku(skuCode, command.sellingPrice());
        if (product.findSkuByCode(skuCode).isPresent() || productRepository.existsBySkuCode(skuCode)) {
            throw new ValidationException("SKU编码已存在: " + skuCode);
        }
        product.addSKU(buildSku(product.getId(), skuCode, command));
        product.touch();
        Product saved = productRepository.save(product);
        domainEventPublisher.publishEventsFrom(product);
        product.clearDomainEvents();
        return saved;
    }

    /**
     * 删除产品下的SKU
     */
    @Transactional
    public Product removeSkuFromProduct(String productId, String skuCode, String tenantId) {
        Product product = requireProduct(productId, tenantId);
        if (!product.removeSKU(skuCode)) {
            throw new EntityNotFoundException("SKU不存在: " + skuCode);
        }
        product.touch();
        Product saved = productRepository.save(product);
        domainEventPublisher.publishEventsFrom(product);
        product.clearDomainEvents();
        return saved;
    }

    /**
     * 根据ID查找产品
     */
    public Product findProductById(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("商品不存在: " + productId));
    }

    /**
     * 根据SKU编码查找产品
     */
    public Product findProductBySkuCode(String skuCode) {
        return productRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new EntityNotFoundException("商品不存在，SKU: " + skuCode));
    }

    /**
     * 分页查询产品
     */
    public PageResult<Product> findProducts(ProductQuery query) {
        return productRepository.findAll(query);
    }

    /**
     * 根据第三方平台的商品ID查找产品（幂等导入使用）
     */
    public java.util.Optional<Product> findProductBySourceId(String tenantId, String sourcePlatform, String sourceProductId) {
        return productRepository.findBySourceProductId(tenantId, sourcePlatform, sourceProductId);
    }

    /**
     * 根据SKU编码查找产品（不抛异常，返回Optional）
     */
    public java.util.Optional<Product> findOptionalProductBySkuCode(String skuCode) {
        return productRepository.findBySkuCode(skuCode);
    }

    /**
     * 保存产品（第三方平台导入场景下的幂等新增/更新）
     */
    @Transactional
    public Product saveImportedProduct(Product product) {
        product.touch();
        Product saved = productRepository.save(product);
        if (product.getDomainEvents() != null && !product.getDomainEvents().isEmpty()) {
            domainEventPublisher.publishEventsFrom(product);
            product.clearDomainEvents();
        }
        return saved;
    }

    private Product requireProduct(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("商品不存在: " + productId));
    }

    private Product requireProduct(String productId, String tenantId) {
        Product product = requireProduct(productId);
        String expectedTenant = StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        if (product.getTenantId() != null && !expectedTenant.equals(product.getTenantId())) {
            throw new EntityNotFoundException("商品不存在: " + productId);
        }
        return product;
    }

    private void replaceSkus(Product product, List<CreateSkuCommand> commands) {
        Set<String> remaining = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (CreateSkuCommand command : commands) {
            String skuCode = normalize(command.skuCode());
            validateSku(skuCode, command.sellingPrice());
            if (!duplicates.add(skuCode)) {
                throw new ValidationException("商品SKU编码重复: " + skuCode);
            }
            boolean ownedByProduct = product.findSkuByCode(skuCode).isPresent();
            if (!ownedByProduct && productRepository.existsBySkuCode(skuCode)) {
                throw new ValidationException("SKU编码已存在: " + skuCode);
            }
            remaining.add(skuCode);
        }
        // 移除本次未提交的SKU（整体覆盖语义）
        List<SKU> toRemove = new ArrayList<>();
        for (SKU sku : product.getSkus()) {
            if (!remaining.contains(sku.getSkuCode())) {
                toRemove.add(sku);
            }
        }
        for (SKU sku : toRemove) {
            product.removeSKU(sku.getSkuCode());
        }
        for (CreateSkuCommand command : commands) {
            String skuCode = normalize(command.skuCode());
            // 复用已有SKU主键，避免覆盖更新时同编码SKU的唯一约束冲突
            String existingSkuId = product.findSkuByCode(skuCode)
                    .map(SKU::getId)
                    .orElseGet(() -> UUID.randomUUID().toString());
            product.replaceSKU(buildSku(product.getId(), skuCode, command, existingSkuId));
        }
    }

    private SKU buildSku(String productId, String skuCode, CreateSkuCommand command) {
        return buildSku(productId, skuCode, command, UUID.randomUUID().toString());
    }

    private SKU buildSku(String productId, String skuCode, CreateSkuCommand command, String skuId) {
        String currency = StringUtils.hasText(command.currency()) ? command.currency().trim() : DEFAULT_CURRENCY;
        ProductPrice price = ProductPrice.builder()
                .sellingPrice(nonEmpty(command.sellingPrice()) ? command.sellingPrice() : BigDecimal.ZERO)
                .costPrice(command.costPrice() != null ? command.costPrice() : BigDecimal.ZERO)
                .currency(currency)
                .build();
        return SKU.builder()
                .id(skuId)
                .skuCode(skuCode)
                .productId(productId)
                .name(StringUtils.hasText(command.skuName()) ? command.skuName().trim() : null)
                .price(price)
                .weightInGrams(command.weightInGrams())
                .volumeInCubicCentimeters(command.volumeInCubicCentimeters())
                .attributes(toAttributes(command.attributes()))
                .active(command.active() == null || command.active())
                .build();
    }

    private List<ProductAttribute> toAttributes(List<ProductAttributeCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductAttribute> attributes = new ArrayList<>(commands.size());
        for (ProductAttributeCommand attributeCommand : commands) {
            if (attributeCommand == null || !StringUtils.hasText(attributeCommand.name())) {
                continue;
            }
            attributes.add(ProductAttribute.builder()
                    .name(attributeCommand.name().trim())
                    .value(attributeCommand.value())
                    .build());
        }
        return attributes;
    }

    /**
     * 解析商品分类：优先使用传入的分类ID，否则按分类名称生成分类
     */
    public ProductCategory resolveCategory(String categoryId, String categoryName) {
        if (!StringUtils.hasText(categoryId) && !StringUtils.hasText(categoryName)) {
            return null;
        }
        String name = StringUtils.hasText(categoryName) ? categoryName.trim() : null;
        if (StringUtils.hasText(categoryId)) {
            return ProductCategory.builder()
                    .id(categoryId.trim())
                    .name(name)
                    .active(true)
                    .build();
        }
        return productCategoryService.createCategory(name, null, null);
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ValidationException("商品名称不能为空");
        }
    }

    private void validateSku(String skuCode, BigDecimal sellingPrice) {
        if (!StringUtils.hasText(skuCode)) {
            throw new ValidationException("SKU编码不能为空");
        }
        if (sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("SKU销售价不能为负数: " + skuCode);
        }
    }

    private boolean nonEmpty(BigDecimal value) {
        return value != null;
    }

    private String resolveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT_ID;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
