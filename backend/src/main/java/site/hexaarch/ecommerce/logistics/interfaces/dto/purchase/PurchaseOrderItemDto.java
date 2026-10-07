package site.hexaarch.ecommerce.logistics.interfaces.dto.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;

import java.math.BigDecimal;

/**
 * 采购明细行出参。
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "采购明细行")
public class PurchaseOrderItemDto {

    @Schema(description = "采购项ID，明细入库后由数据库自增生成；新建未落库时为空")
    private String id;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "小计 = 单价 × 数量")
    private BigDecimal totalPrice;

    /**
     * 由领域采购项构造DTO。
     *
     * @param item 领域采购项
     * @return DTO，入参为null时返回null
     */
    public static PurchaseOrderItemDto from(PurchaseOrderItem item) {
        if (item == null) {
            return null;
        }
        PurchaseOrderItemDto dto = new PurchaseOrderItemDto();
        dto.setId(item.getId());
        dto.setSkuCode(item.getSkuCode());
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setTotalPrice(item.getTotalPrice());
        return dto;
    }
}
