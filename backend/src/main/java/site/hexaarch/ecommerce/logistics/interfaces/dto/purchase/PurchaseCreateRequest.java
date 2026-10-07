package site.hexaarch.ecommerce.logistics.interfaces.dto.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建采购单请求体。
 * <p>
 * tenantId 可省略，省略时由控制器按「请求参数 &gt; 登录租户上下文 &gt; 默认租户」解析；
 * 显式传入时必须等于登录租户，否则被 TenantIsolationAspect 判为跨租户。
 * </p>
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "创建采购单入参")
public class PurchaseCreateRequest {

    @Schema(description = "租户ID，可省略")
    private String tenantId;

    @Schema(description = "供应商ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supplierId;

    @Schema(description = "供应商名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supplierName;

    @Schema(description = "收货仓库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warehouseId;

    @Schema(description = "采购明细行，可为空（建单后逐条追加）")
    private List<PurchaseItemRequest> items;

    /**
     * 转换为领域采购项，字段校验仍由聚合的 addItem 负责。
     *
     * @return 领域采购项列表
     */
    public List<PurchaseOrderItem> toDomainItems() {
        List<PurchaseOrderItem> domainItems = new ArrayList<>();
        if (items == null) {
            return domainItems;
        }
        for (PurchaseItemRequest item : items) {
            if (item == null) {
                continue;
            }
            domainItems.add(PurchaseOrderItem.create(item.getSkuCode(), item.getProductName(),
                    item.getQuantity(), item.getUnitPrice()));
        }
        return domainItems;
    }
}
