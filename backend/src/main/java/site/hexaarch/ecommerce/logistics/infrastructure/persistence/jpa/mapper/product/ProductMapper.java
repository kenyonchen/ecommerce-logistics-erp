package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductAttribute;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.SkuJpaEntity;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductAttributeDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductSkuDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 产品映射器，用于在产品领域模型、JPA实体与DTO之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    String DEFAULT_CURRENCY = "CNY";

    /**
     * 将产品领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "sourcePlatform", target = "sourcePlatform")
    @Mapping(source = "sourceProductId", target = "sourceProductId")
    @Mapping(source = "skus", target = "skus")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    ProductJpaEntity toJpaEntity(Product product);

    /**
     * 将SKU领域实体转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "name", target = "skuName")
    @Mapping(source = "price.sellingPrice", target = "sellingPrice", qualifiedByName = "bigDecimalToDouble")
    @Mapping(source = "price.costPrice", target = "costPrice", qualifiedByName = "bigDecimalToDouble")
    @Mapping(source = "price.currency", target = "currency", defaultValue = DEFAULT_CURRENCY)
    @Mapping(source = "weightInGrams", target = "weightInGrams", qualifiedByName = "intToDouble")
    @Mapping(source = "volumeInCubicCentimeters", target = "volumeInCubicCentimeters")
    @Mapping(source = "attributes", target = "attributesJson", qualifiedByName = "attributesToJson")
    @Mapping(source = "active", target = "active")
    SkuJpaEntity toJpaEntity(SKU sku);

    /**
     * 将产品JPA实体转换为产品领域模型
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(target = "category", expression = "java(createProductCategory(productJpaEntity))")
    @Mapping(source = "sourcePlatform", target = "sourcePlatform")
    @Mapping(source = "sourceProductId", target = "sourceProductId")
    @Mapping(source = "skus", target = "skus")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(target = "domainEvents", ignore = true)
    Product toDomainAggregate(ProductJpaEntity productJpaEntity);

    /**
     * 将SKU JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "skuName", target = "name")
    @Mapping(target = "productId", expression = "java(resolveProductId(skuJpaEntity))")
    @Mapping(target = "price", expression = "java(toProductPrice(skuJpaEntity))")
    @Mapping(source = "weightInGrams", target = "weightInGrams", qualifiedByName = "doubleToInt")
    @Mapping(source = "volumeInCubicCentimeters", target = "volumeInCubicCentimeters")
    @Mapping(source = "attributesJson", target = "attributes", qualifiedByName = "jsonToAttributes")
    @Mapping(source = "active", target = "active")
    SKU toDomainEntity(SkuJpaEntity skuJpaEntity);

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
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "sourcePlatform", target = "sourcePlatform")
    @Mapping(source = "sourceProductId", target = "sourceProductId")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "skus", target = "skus")
    ProductDto toDto(Product product);

    /**
     * 将产品列表转换为DTO列表
     */
    List<ProductDto> toDtoList(List<Product> products);

    /**
     * 将SKU领域实体转换为SKU DTO
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "name", target = "skuName")
    @Mapping(source = "price.sellingPrice", target = "sellingPrice")
    @Mapping(source = "price.costPrice", target = "costPrice")
    @Mapping(source = "price.currency", target = "currency", defaultValue = DEFAULT_CURRENCY)
    @Mapping(source = "weightInGrams", target = "weightInGrams")
    @Mapping(source = "volumeInCubicCentimeters", target = "volumeInCubicCentimeters")
    @Mapping(target = "attributes", expression = "java(toAttributeDtoList(sku.getAttributes()))")
    @Mapping(source = "active", target = "active")
    ProductSkuDto toSkuDto(SKU sku);

    /**
     * 将SKU领域实体列表转换为SKU DTO列表
     */
    List<ProductSkuDto> toSkuDtoList(List<SKU> skus);

    /**
     * 保存后回填SKU与产品的双向引用，否则 {@code skus.product_id} 会因非空约束写入失败。
     */
    @AfterMapping
    default void linkProductToSkus(@MappingTarget ProductJpaEntity productJpaEntity) {
        if (productJpaEntity.getSkus() == null) {
            return;
        }
        for (SkuJpaEntity sku : productJpaEntity.getSkus()) {
            sku.setProduct(productJpaEntity);
        }
    }

    @Named("bigDecimalToDouble")
    default Double bigDecimalToDouble(BigDecimal bigDecimal) {
        return bigDecimal != null ? bigDecimal.doubleValue() : null;
    }

    @Named("intToDouble")
    default Double intToDouble(Integer value) {
        return value != null ? value.doubleValue() : null;
    }

    @Named("doubleToInt")
    default Integer doubleToInt(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }

    @Named("attributesToJson")
    default String attributesToJson(List<ProductAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(attributes);
        } catch (Exception e) {
            return null;
        }
    }

    @Named("jsonToAttributes")
    default List<ProductAttribute> jsonToAttributes(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ObjectMapper().readValue(attributesJson, new TypeReference<List<ProductAttribute>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    default ProductPrice toProductPrice(SkuJpaEntity skuJpaEntity) {
        String currency = skuJpaEntity.getCurrency() == null || skuJpaEntity.getCurrency().isBlank()
                ? DEFAULT_CURRENCY
                : skuJpaEntity.getCurrency();
        return ProductPrice.builder()
                .sellingPrice(toBigDecimal(skuJpaEntity.getSellingPrice()))
                .costPrice(toBigDecimal(skuJpaEntity.getCostPrice()))
                .currency(currency)
                .build();
    }

    default BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    default String resolveProductId(SkuJpaEntity skuJpaEntity) {
        return skuJpaEntity.getProduct() != null ? skuJpaEntity.getProduct().getId() : null;
    }

    default List<ProductAttributeDto> toAttributeDtoList(List<ProductAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductAttributeDto> result = new ArrayList<>(attributes.size());
        for (ProductAttribute attribute : attributes) {
            result.add(new ProductAttributeDto(attribute.getName(), attribute.getValue()));
        }
        return result;
    }

    /**
     * 创建产品分类领域实体
     */
    @Named("createProductCategory")
    default ProductCategory createProductCategory(ProductJpaEntity productJpaEntity) {
        if (productJpaEntity.getCategoryId() == null && productJpaEntity.getCategoryName() == null) {
            return null;
        }
        return ProductCategory.builder()
                .id(productJpaEntity.getCategoryId())
                .name(productJpaEntity.getCategoryName())
                .active(true)
                .build();
    }
}
