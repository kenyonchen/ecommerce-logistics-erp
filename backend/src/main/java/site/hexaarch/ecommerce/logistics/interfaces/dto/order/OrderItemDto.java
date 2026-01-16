package site.hexaarch.ecommerce.logistics.interfaces.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单行项DTO，用于接收订单行项的请求参数。
 *
 * @author kenyon
 */
@Data
public class OrderItemDto {
    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "iPhone 13", minLength = 1, maxLength = 100)
    private String productName;
    @Schema(description = "SKU编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "IPHONE13-BLK-128G", minLength = 1, maxLength = 50)
    private String skuCode;
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "2", minimum = "1")
    private Integer quantity;
    @Schema(description = "单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "6999.00", minimum = "0")
    private Double unitPrice;
    @Schema(description = "总价", requiredMode = Schema.RequiredMode.REQUIRED, example = "13998.00", minimum = "0")
    private Double totalPrice;
}
