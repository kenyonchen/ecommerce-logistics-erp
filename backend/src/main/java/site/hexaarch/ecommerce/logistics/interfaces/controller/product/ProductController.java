package site.hexaarch.ecommerce.logistics.interfaces.controller.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateProductCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.CreateSkuCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.ProductAttributeCommand;
import site.hexaarch.ecommerce.logistics.application.command.product.UpdateProductCommand;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductQuery;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapper;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.CreateProductRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.SkuRequestDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.UpdateProductRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品控制器，处理产品和SKU相关的HTTP请求。
 * <p>
 * 接口约定：所有响应统一为 {@code Result<T>}，成功码为字符串 {@code "200"}。
 * </p>
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

    @Operation(summary = "分页查询产品", description = "支持关键字、分类、状态、来源平台过滤的分页查询")
    @GetMapping
    public Result<PageResult<ProductDto>> listProducts(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "关键字（匹配商品名称/描述/SKU编码）") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类名称") @RequestParam(required = false) String categoryName,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean active,
            @Parameter(description = "来源平台") @RequestParam(required = false) String sourcePlatform,
            @Parameter(description = "SKU编码") @RequestParam(required = false) String skuCode,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "排序，格式：字段,asc|desc") @RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {
        ProductQuery query = ProductQuery.builder()
                .tenantId(tenantId)
                .keyword(keyword)
                .categoryName(categoryName)
                .active(active)
                .sourcePlatform(sourcePlatform)
                .skuCode(skuCode)
                .page(page)
                .size(size)
                .sort(sort)
                .build();
        PageResult<Product> products = productApplicationService.findProducts(query);
        return Result.success(products.map(productMapper::toDto));
    }

    @Operation(summary = "创建产品", description = "创建一个新的产品，可同时携带SKU列表")
    @PostMapping
    public Result<ProductDto> createProduct(@RequestBody CreateProductRequest request) {
        var product = productApplicationService.createProduct(toCreateCommand(request));
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "更新产品", description = "更新已存在的产品信息，SKU列表为整体覆盖")
    @PutMapping("/{id}")
    public Result<ProductDto> updateProduct(@Parameter(description = "产品ID") @PathVariable String id,
                                            @RequestBody UpdateProductRequest request) {
        var command = new UpdateProductCommand(
                request.getName(),
                request.getDescription(),
                request.getCategoryId(),
                request.getCategoryName(),
                request.getActive(),
                toSkuCommands(request.getSkus()));
        var product = productApplicationService.updateProduct(id, command);
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

    @Operation(summary = "删除产品", description = "根据ID删除产品及其SKU")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@Parameter(description = "产品ID") @PathVariable String id,
                                      @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        productApplicationService.deleteProduct(id, tenantId);
        return Result.success();
    }

    @Operation(summary = "新增SKU", description = "给指定产品新增SKU")
    @PostMapping("/{id}/skus")
    public Result<ProductDto> addSku(@Parameter(description = "产品ID") @PathVariable String id,
                                     @RequestBody SkuRequestDto request) {
        var product = productApplicationService.addSkuToProduct(id, toSkuCommand(request));
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "删除SKU", description = "删除指定产品下的SKU")
    @DeleteMapping("/{id}/skus/{skuCode}")
    public Result<ProductDto> removeSku(@Parameter(description = "产品ID") @PathVariable String id,
                                        @Parameter(description = "SKU编码") @PathVariable String skuCode,
                                        @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        var product = productApplicationService.removeSkuFromProduct(id, skuCode, tenantId);
        return Result.success(productMapper.toDto(product));
    }

    @Operation(summary = "批量启用/停用产品", description = "批量修改产品的启用状态")
    @PutMapping("/batch/active")
    public Result<Integer> batchChangeActive(@RequestBody BatchActiveRequest request) {
        int count = productApplicationService.batchChangeActive(request.getProductIds(), request.getTenantId(),
                request.getActive() == null || request.getActive());
        return Result.success(count);
    }

    @Operation(summary = "批量删除产品", description = "批量删除多个产品及其SKU")
    @DeleteMapping("/batch")
    public Result<Integer> batchDeleteProducts(@RequestBody BatchDeleteRequest request) {
        int count = productApplicationService.batchDeleteProducts(request.getProductIds(), request.getTenantId());
        return Result.success(count);
    }

    private CreateProductCommand toCreateCommand(CreateProductRequest request) {
        return new CreateProductCommand(
                request.getTenantId(),
                request.getName(),
                request.getDescription(),
                request.getCategoryId(),
                request.getCategoryName(),
                null,
                null,
                request.getActive(),
                toSkuCommands(request.getSkus()));
    }

    private List<CreateSkuCommand> toSkuCommands(List<SkuRequestDto> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        List<CreateSkuCommand> commands = new ArrayList<>(requests.size());
        for (SkuRequestDto request : requests) {
            commands.add(toSkuCommand(request));
        }
        return commands;
    }

    private CreateSkuCommand toSkuCommand(SkuRequestDto request) {
        List<ProductAttributeCommand> attributes = new ArrayList<>();
        if (request.getAttributes() != null) {
            request.getAttributes().forEach(attribute ->
                    attributes.add(new ProductAttributeCommand(attribute.getName(), attribute.getValue())));
        }
        return new CreateSkuCommand(
                request.getSkuCode(),
                request.getSkuName(),
                request.getSellingPrice(),
                request.getCostPrice(),
                request.getCurrency(),
                request.getWeightInGrams(),
                request.getVolumeInCubicCentimeters(),
                attributes,
                request.getActive());
    }

    /**
     * 批量启用/停用请求体
     */
    public static class BatchActiveRequest {
        private List<String> productIds;
        private String tenantId;
        private Boolean active;

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    /**
     * 批量删除请求体
     */
    public static class BatchDeleteRequest {
        private List<String> productIds;
        private String tenantId;

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }
}
