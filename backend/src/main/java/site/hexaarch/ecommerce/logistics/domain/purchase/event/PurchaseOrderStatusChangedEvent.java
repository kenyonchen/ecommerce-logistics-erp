package site.hexaarch.ecommerce.logistics.domain.purchase.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.time.LocalDateTime;

/**
 * 采购单状态变更事件，当采购单状态发生变化时触发。
 *
 * @author kenyon
 */
@Getter
public class PurchaseOrderStatusChangedEvent extends BaseDomainEvent {
    private final String purchaseOrderId;
    private final PurchaseStatus oldStatus;
    private final PurchaseStatus newStatus;
    private final LocalDateTime updatedAt;

    /**
     * 构造函数。
     *
     * @param purchaseOrderId 采购单号
     * @param oldStatus       旧状态
     * @param newStatus       新状态
     * @param updatedAt       更新时间
     */
    public PurchaseOrderStatusChangedEvent(String purchaseOrderId, PurchaseStatus oldStatus, PurchaseStatus newStatus, LocalDateTime updatedAt) {
        this.purchaseOrderId = purchaseOrderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.updatedAt = updatedAt;
    }
}
