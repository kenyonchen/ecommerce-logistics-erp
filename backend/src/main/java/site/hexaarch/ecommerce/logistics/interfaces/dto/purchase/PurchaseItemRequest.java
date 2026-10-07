package site.hexaarch.ecommerce.logistics.interfaces.dto.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;

import java.math.BigDecimal;

/**
 * 采购项请求体。
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "采购明细行入参")
public class PurchaseItemRequest {

    @Schema(description = "SKU编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skuCode;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;

    @Schema(description = "数量，必须大于0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @Schema(description = "单价，必须大于0", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal unitPrice;

    /**
     * 构造领域采购项（校验由领域层完成：必填、数量与单价必须为正）。
     *
     * @return 领域采购项
     */
    public PurchaseOrderItem toDomainItem() {
        return PurchaseOrderItem.create(skuCode, productName, quantity, unitPrice);
    }
}
