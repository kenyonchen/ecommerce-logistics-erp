package site.hexaarch.ecommerce.logistics.domain.product.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductUpdatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.event.SKUCreatedEvent;

import java.util.ArrayList;
import java.util.List;
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
    private final ProductCategory category;
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

    private Product(String id, String tenantId, ProductCategory category, List<Object> domainEvents, String name, String description, List<SKU> skus, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.category = category;
        this.domainEvents = domainEvents;
        this.name = name;
        this.description = description;
        this.skus = skus;
        this.active = active;
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
            return new Product(id, tenantId, category, domainEvents, name, description, skus, active);
        }
    }
}
