package site.hexaarch.ecommerce.logistics.domain.product.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductUpdatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.event.SKUCreatedEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 产品聚合根，负责管理SKU和产品属性。
 *
 * @author kenyon
 */
@Getter
public class Product {
    /**
     * 产品唯一标识
     */
    private final String id;
    /**
     * 租户ID
     */
    private final String tenantId;
    /**
     * 产品分类
     */
    private ProductCategory category;
    /**
     * 领域事件列表
     */
    private final List<Object> domainEvents;
    /**
     * 产品名称
     */
    private String name;
    /**
     * 产品描述
     */
    private String description;
    /**
     * 产品SKU列表
     */
    private List<SKU> skus;
    /**
     * 是否激活
     */
    private boolean active;
    /**
     * 来源平台（第三方电商平台名称，手动创建时为空）
     */
    private final String sourcePlatform;
    /**
     * 来源平台侧的商品ID，用于重复导入时的幂等更新
     */
    private final String sourceProductId;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    private Product(String id, String tenantId, ProductCategory category, List<Object> domainEvents, String name, String description, List<SKU> skus, boolean active, String sourcePlatform, String sourceProductId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.category = category;
        // 兜底初始化：从持久层重建（Mapper/Builder 未传入 domainEvents）时该字段为 null，
        // 注册领域事件会 NPE（ISS-068），此处保证事件列表恒非空
        this.domainEvents = domainEvents == null ? new ArrayList<>() : domainEvents;
        this.name = name;
        this.description = description;
        this.skus = skus;
        this.active = active;
        this.sourcePlatform = sourcePlatform;
        this.sourceProductId = sourceProductId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建产品静态工厂方法
     */
    public static Product create(String tenantId, String name, String description, ProductCategory category) {
        var product = Product.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .category(category)
                .skus(new ArrayList<>())
                .active(true)
                .domainEvents(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 添加产品创建事件
        product.domainEvents.add(ProductCreatedEvent.builder()
                .productId(product.id)
                .tenantId(product.tenantId)
                .productName(product.name)
                .timestamp(System.currentTimeMillis())
                .build());

        return product;
    }

    /**
     * 更新产品信息
     */
    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;

        // 添加产品更新事件
        this.domainEvents.add(ProductUpdatedEvent.builder()
                .productId(this.id)
                .productName(this.name)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 添加SKU
     */
    public void addSKU(SKU sku) {
        if (this.skus == null) {
            this.skus = new ArrayList<>();
        }
        this.skus.add(sku);

        // 添加SKU创建事件
        this.domainEvents.add(SKUCreatedEvent.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .productId(this.getId())
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 根据SKU编码查找SKU
     */
    public Optional<SKU> findSkuByCode(String skuCode) {
        if (this.skus == null || skuCode == null) {
            return Optional.empty();
        }
        return this.skus.stream()
                .filter(sku -> skuCode.equals(sku.getSkuCode()))
                .findFirst();
    }

    /**
     * 替换SKU：如已存在相同SKU编码则先移除再新增，保证SKU编码在产品内唯一。
     * 第三方平台导入场景下用于幂等更新。
     */
    public void replaceSKU(SKU sku) {
        this.skus = this.skus == null ? new ArrayList<>() : this.skus;
        this.skus.removeIf(existing -> existing.getSkuCode() != null
                && existing.getSkuCode().equals(sku.getSkuCode()));
        this.skus.add(sku);

        this.domainEvents.add(SKUCreatedEvent.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .productId(this.getId())
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 移除指定SKU编码的SKU
     *
     * @return 是否真的移除了SKU
     */
    public boolean removeSKU(String skuCode) {
        if (this.skus == null || skuCode == null) {
            return false;
        }
        return this.skus.removeIf(sku -> skuCode.equals(sku.getSkuCode()));
    }

    /**
     * 更新产品分类
     */
    public void updateCategory(ProductCategory newCategory) {
        this.category = newCategory;
    }

    /**
     * 激活产品
     */
    public void activate() {
        this.active = true;
    }

    /**
     * 停用产品
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * 刷新更新时间
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清除领域事件
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    public static class Builder {
        private String id;
        private String tenantId;
        private ProductCategory category;
        private List<Object> domainEvents;
        private String name;
        private String description;
        private List<SKU> skus;
        private boolean active;
        private String sourcePlatform;
        private String sourceProductId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder sourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
            return this;
        }

        public Builder sourceProductId(String sourceProductId) {
            this.sourceProductId = sourceProductId;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder category(ProductCategory category) {
            this.category = category;
            return this;
        }

        public Builder domainEvents(List<Object> domainEvents) {
            this.domainEvents = domainEvents;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder skus(List<SKU> skus) {
            this.skus = skus;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Product build() {
            return new Product(id, tenantId, category, domainEvents, name, description, skus, active, sourcePlatform, sourceProductId, createdAt, updatedAt);
        }
    }
}
