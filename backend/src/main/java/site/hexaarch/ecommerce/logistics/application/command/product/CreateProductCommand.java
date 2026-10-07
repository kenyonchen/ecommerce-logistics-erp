package site.hexaarch.ecommerce.logistics.application.command.product;

import java.util.List;

/**
 * 创建商品命令（含SKU明细）。
 *
 * @author kenyon
 */
public record CreateProductCommand(
        String tenantId,
        String name,
        String description,
        String categoryId,
        String categoryName,
        String sourcePlatform,
        String sourceProductId,
        Boolean active,
        List<CreateSkuCommand> skus
) {
}
