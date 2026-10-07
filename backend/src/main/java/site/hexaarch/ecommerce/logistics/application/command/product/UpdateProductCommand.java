package site.hexaarch.ecommerce.logistics.application.command.product;

import java.util.List;

/**
 * 更新商品命令，SKU列表为整体覆盖。
 *
 * @author kenyon
 */
public record UpdateProductCommand(
        String name,
        String description,
        String categoryId,
        String categoryName,
        Boolean active,
        List<CreateSkuCommand> skus
) {
}
