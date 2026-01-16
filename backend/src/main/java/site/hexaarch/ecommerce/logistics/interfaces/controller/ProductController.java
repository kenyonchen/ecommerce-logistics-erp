package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.ProductApplicationService;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.ProductMapper;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductDto;

/**
 * 产品控制器，处理产品和SKU相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "产品管理", description = "产品和SKU相关的API接口")
public class ProductController {
    private final ProductApplicationService productApplicationService;
    private final ProductMapper productMapper;

    public ProductController(ProductApplicationService productApplicationService, ProductMapper productMapper) {
        this.productApplicationService = productApplicationService;
        this.productMapper = productMapper;
    }

    @Operation(summary = "创建产品", description = "创建一个新的产品")
    @PostMapping
    public Result<ProductDto> createProduct(
            @RequestParam(defaultValue = "default-tenant") String tenantId,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam String categoryName) {
        var category = ProductCategory.builder()
                .id(java.util.UUID.randomUUID().toString())
                .name(categoryName)
                .build();
        var product = productApplicationService.createProduct(tenantId, name, description, category);
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "根据ID获取产品", description = "根据产品ID获取产品详细信息")
    @GetMapping("/{id}")
    public Result<ProductDto> getProductById(
            @Parameter(description = "产品ID") @PathVariable String id) {
        var product = productApplicationService.findProductById(id);
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "根据SKU编码获取产品", description = "根据SKU编码查找产品")
    @GetMapping("/sku/{skuCode}")
    public Result<ProductDto> getProductBySkuCode(
            @Parameter(description = "SKU编码") @PathVariable String skuCode) {
        var product = productApplicationService.findProductBySkuCode(skuCode);
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "激活产品", description = "激活指定ID的产品")
    @PutMapping("/{id}/activate")
    public Result<ProductDto> activateProduct(
            @Parameter(description = "产品ID") @PathVariable String id) {
        var product = productApplicationService.activateProduct(id);
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "停用产品", description = "停用指定ID的产品")
    @PutMapping("/{id}/deactivate")
    public Result<ProductDto> deactivateProduct(
            @Parameter(description = "产品ID") @PathVariable String id) {
        var product = productApplicationService.deactivateProduct(id);
        return Result.success(productMapper.toDto(product));
    }
}
