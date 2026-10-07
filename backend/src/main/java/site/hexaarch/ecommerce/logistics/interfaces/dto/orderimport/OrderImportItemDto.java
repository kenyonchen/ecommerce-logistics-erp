package site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 标准化后的订单行项DTO（导入预览与导入执行共用）
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入订单行项")
public class OrderImportItemDto {
    @Schema(description = "SKU编码")
    private String sku;
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "数量")
    private Integer quantity;
    @Schema(description = "单价")
    private BigDecimal unitPrice;
    @Schema(description = "小计")
    private BigDecimal totalPrice;
}
