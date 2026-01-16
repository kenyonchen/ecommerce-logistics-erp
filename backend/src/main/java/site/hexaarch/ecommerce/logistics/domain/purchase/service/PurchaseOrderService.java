package site.hexaarch.ecommerce.logistics.domain.purchase.service;

import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.util.List;

/**
 * 采购单领域服务，封装采购单相关的业务逻辑。
 *
 * @author kenyon
 */
public interface PurchaseOrderService {
    /**
     * 创建采购单。
     *
     * @param tenantId     租户ID
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param warehouseId  仓库ID
     * @return 创建的采购单
     */
    PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId);

    /**
     * 根据采购单号查找采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    PurchaseOrder findPurchaseOrderById(String purchaseOrderId);

    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    List<PurchaseOrder> findPurchaseOrdersByStatus(PurchaseStatus status);

    /**
     * 批准采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 批准后的采购单
     */
    PurchaseOrder approvePurchaseOrder(String purchaseOrderId);

    /**
     * 拒绝采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 拒绝后的采购单
     */
    PurchaseOrder rejectPurchaseOrder(String purchaseOrderId);

    /**
     * 标记采购单为运输中。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    PurchaseOrder markPurchaseOrderAsInTransit(String purchaseOrderId);

    /**
     * 标记采购单为已送达。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    PurchaseOrder markPurchaseOrderAsDelivered(String purchaseOrderId);

    /**
     * 取消采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    PurchaseOrder cancelPurchaseOrder(String purchaseOrderId);
}
