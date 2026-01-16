package site.hexaarch.ecommerce.logistics.domain.purchase.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.util.List;
import java.util.UUID;

/**
 * 采购单领域服务的实现类，封装采购单相关的业务逻辑。
 *
 * @author kenyon
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;

    /**
     * 构造函数，注入采购单仓储。
     *
     * @param purchaseOrderRepository 采购单仓储
     */
    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    /**
     * 创建采购单。
     *
     * @param tenantId     租户ID
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param warehouseId  仓库ID
     * @return 创建的采购单
     */
    @Override
    public PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId) {
        var purchaseOrderId = "PO-" + UUID.randomUUID().toString().substring(0, 8);
        var purchaseOrder = PurchaseOrder.create(purchaseOrderId, tenantId, supplierId, supplierName, warehouseId);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 根据采购单号查找采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    @Override
    public PurchaseOrder findPurchaseOrderById(String purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("采购单不存在: " + purchaseOrderId));
    }

    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    @Override
    public List<PurchaseOrder> findPurchaseOrdersByStatus(PurchaseStatus status) {
        return purchaseOrderRepository.findByStatus(status);
    }

    /**
     * 批准采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 批准后的采购单
     */
    @Override
    public PurchaseOrder approvePurchaseOrder(String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.approve();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 拒绝采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 拒绝后的采购单
     */
    @Override
    public PurchaseOrder rejectPurchaseOrder(String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.reject();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为运输中。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder markPurchaseOrderAsInTransit(String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.markAsInTransit();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为已送达。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder markPurchaseOrderAsDelivered(String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.markAsDelivered();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 取消采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    @Override
    public PurchaseOrder cancelPurchaseOrder(String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.cancel();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }
}
