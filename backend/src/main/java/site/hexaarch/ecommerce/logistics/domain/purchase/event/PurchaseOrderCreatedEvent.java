package site.hexaarch.ecommerce.logistics.domain.purchase.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;

import java.time.LocalDateTime;

/**
 * 采购单创建事件，当新采购单创建时触发。
 *
 * @author kenyon
 */
@Getter
public class PurchaseOrderCreatedEvent extends BaseDomainEvent {
    private final String purchaseOrderId;
    private final String tenantId;
    private final String supplierId;
    private final String warehouseId;
    private final LocalDateTime createdAt;

    /**
     * 构造函数。
     *
     * @param purchaseOrderId 采购单号
     * @param tenantId        租户ID
     * @param supplierId      供应商ID
     * @param warehouseId     仓库ID
     * @param createdAt       创建时间
     */
    public PurchaseOrderCreatedEvent(String purchaseOrderId, String tenantId, String supplierId, String warehouseId, LocalDateTime createdAt) {
        this.purchaseOrderId = purchaseOrderId;
        this.tenantId = tenantId;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.createdAt = createdAt;
    }
}
