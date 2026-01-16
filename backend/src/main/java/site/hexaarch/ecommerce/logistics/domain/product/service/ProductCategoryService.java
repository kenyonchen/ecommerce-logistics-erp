package site.hexaarch.ecommerce.logistics.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;

/**
 * 产品分类服务，管理产品分类，如创建、修改分类。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    /**
     * 创建产品分类
     */
    public ProductCategory createCategory(String name, String description, ProductCategory parentCategory) {
        return ProductCategory.builder()
                .id(java.util.UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .parentId(parentCategory != null ? parentCategory.getId() : null)
                .level(parentCategory != null ? parentCategory.getLevel() + 1 : 1)
                .active(true)
                .build();
    }

    /**
     * 更新产品分类
     */
    public ProductCategory updateCategory(ProductCategory category, String name, String description) {
        category.updateInfo(name, description);
        return category;
    }

    /**
     * 激活产品分类
     */
    public ProductCategory activateCategory(ProductCategory category) {
        category.activate();
        return category;
    }

    /**
     * 停用产品分类
     */
    public ProductCategory deactivateCategory(ProductCategory category) {
        category.deactivate();
        return category;
    }
}
