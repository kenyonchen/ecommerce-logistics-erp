package site.hexaarch.ecommerce.logistics.domain.product.aggregate;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.event.ProductUpdatedEvent;
import site.hexaarch.ecommerce.logistics.domain.product.event.SKUCreatedEvent;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapperImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产品聚合根领域事件注册回归测试（ISS-068）：
 * 从持久层重建的聚合（不显式传入 domainEvents）必须能注册领域事件而不抛 NPE。
 */
class ProductTest {

    /** 模拟 ProductMapper.toDomainAggregate 重建出来的聚合：domainEvents 未显式赋值 */
    private Product reconstructed() {
        return Product.builder()
                .id("p-1")
                .tenantId("default-tenant")
                .name("已有商品")
                .description("旧描述")
                .category(ProductCategory.builder().id("c1").name("ELECTRONICS").active(true).build())
                .skus(new ArrayList<>(List.of(SKU.builder().id("s1").skuCode("SKU-001").productId("p-1").build())))
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void reconstructedAggregateShouldHaveNonNullDomainEvents() {
        assertNotNull(reconstructed().getDomainEvents(), "重建聚合后 domainEvents 必须非空");
        assertTrue(reconstructed().getDomainEvents().isEmpty());
    }

    @Test
    void updateInfoShouldRegisterEventOnReconstructedAggregate() {
        Product product = reconstructed();
        assertDoesNotThrow(() -> product.updateInfo("新名称", "新描述"));
        assertFalse(product.getDomainEvents().isEmpty());
        assertTrue(product.getDomainEvents().get(0) instanceof ProductUpdatedEvent);
    }

    @Test
    void addAndReplaceSkuShouldRegisterEventOnReconstructedAggregate() {
        Product product = reconstructed();
        assertDoesNotThrow(() -> product.addSKU(SKU.builder().id("s2").skuCode("SKU-002").productId("p-1").build()));
        assertDoesNotThrow(() -> product.replaceSKU(SKU.builder().id("s3").skuCode("SKU-003").productId("p-1").build()));
        assertTrue(product.getDomainEvents().stream().anyMatch(event -> event instanceof SKUCreatedEvent));
    }

    @Test
    void mapperRebuiltAggregateShouldRegisterDomainEventWithoutThrowing() {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setId("p-2");
        entity.setTenantId("default-tenant");
        entity.setName("库中商品");
        entity.setDescription("描述");
        entity.setCategoryId("c1");
        entity.setCategoryName("ELECTRONICS");
        entity.setActive(true);
        entity.setSkus(new ArrayList<>());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        Product product = new ProductMapperImpl().toDomainAggregate(entity);

        assertNotNull(product.getDomainEvents(), "mapper 重建聚合后 domainEvents 必须非空");
        assertDoesNotThrow(() -> product.updateInfo("改名", "改描述"));
        assertFalse(product.getDomainEvents().isEmpty());
    }

    @Test
    void createFactoryShouldStillRegisterProductCreatedEvent() {
        Product product = Product.create("default-tenant", "新商品", "描述", null);
        assertFalse(product.getDomainEvents().isEmpty());
        product.clearDomainEvents();
        assertTrue(product.getDomainEvents().isEmpty());
    }
}
