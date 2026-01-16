package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.ProductJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.SkuJpaEntity;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductDto;

import java.util.List;

/**
 * 产品映射器，用于在产品领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    /**
     * 将产品领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "skus", target = "skus")
    @Mapping(source = "active", target = "active")
    ProductJpaEntity toJpaEntity(Product product);

    /**
     * 将JPA实体转换为产品领域模型
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    ProductCategory toDomainCategory(ProductJpaEntity productJpaEntity);

    /**
     * 将SKU领域实体转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "skuCode", target = "skuName")
    @Mapping(source = "price.sellingPrice", target = "price", qualifiedByName = "bigDecimalToDouble")
    @Mapping(source = "weightInGrams", target = "weight")
    @Mapping(source = "volumeInCubicCentimeters", target = "dimensions")
    SkuJpaEntity toJpaEntity(SKU sku);

    /**
     * 将SKU JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "skuName", target = "skuCode")
    @Mapping(source = "price", target = "price", qualifiedByName = "doubleToProductPrice")
    @Mapping(source = "weight", target = "weightInGrams")
    @Mapping(source = "dimensions", target = "volumeInCubicCentimeters")
    SKU toDomainEntity(SkuJpaEntity skuJpaEntity);

    /**
     * 将产品JPA实体转换为产品领域模型
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(target = "category", expression = "java(createProductCategory(productJpaEntity))")
    @Mapping(source = "skus", target = "skus")
    @Mapping(source = "active", target = "active")
    Product toDomainAggregate(ProductJpaEntity productJpaEntity);

    @Named("bigDecimalToDouble")
    default Double bigDecimalToDouble(java.math.BigDecimal bigDecimal) {
        return bigDecimal != null ? bigDecimal.doubleValue() : null;
    }

    @Named("doubleToProductPrice")
    default site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice doubleToProductPrice(Double price) {
        if (price == null) {
            return null;
        }
        return site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice.builder()
                .sellingPrice(java.math.BigDecimal.valueOf(price))
                .costPrice(java.math.BigDecimal.valueOf(price))
                .currency("USD")
                .build();
    }

    /**
     * 创建产品分类领域实体
     */
    @Named("createProductCategory")
    default ProductCategory createProductCategory(ProductJpaEntity productJpaEntity) {
        if (productJpaEntity.getCategoryId() == null || productJpaEntity.getCategoryName() == null) {
            return null;
        }
        return ProductCategory.builder()
                .id(productJpaEntity.getCategoryId())
                .name(productJpaEntity.getCategoryName())
                .build();
    }

    /**
     * 将SKU列表转换为JPA实体列表
     */
    List<SkuJpaEntity> toSkuJpaEntityList(List<SKU> skus);

    /**
     * 将SKU JPA实体列表转换为领域实体列表
     */
    List<SKU> toSkuDomainEntityList(List<SkuJpaEntity> skuJpaEntities);

    /**
     * 将产品领域模型转换为DTO
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "category", target = "category")
    @Mapping(source = "active", target = "active")
    ProductDto toDto(Product product);

    /**
     * 将产品列表转换为DTO列表
     */
    List<ProductDto> toDtoList(List<Product> products);
}