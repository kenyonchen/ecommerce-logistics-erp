package site.hexaarch.ecommerce.logistics.application.service.purchase;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.service.PurchaseOrderService;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购应用服务，负责协调领域对象完成采购相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class PurchaseApplicationService {
    private final PurchaseOrderService purchaseOrderService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造函数，注入采购单领域服务。
     *
     * @param purchaseOrderService 采购单领域服务
     * @param domainEventPublisher 领域事件发布服务
     */
    public PurchaseApplicationService(PurchaseOrderService purchaseOrderService, DomainEventPublisher domainEventPublisher) {
        this.purchaseOrderService = purchaseOrderService;
        this.domainEventPublisher = domainEventPublisher;
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
    public PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId) {
        PurchaseOrder purchaseOrder = purchaseOrderService.createPurchaseOrder(tenantId, supplierId, supplierName, warehouseId);
        // 发布采购单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 根据采购单号查找采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    public PurchaseOrder findPurchaseOrderById(String purchaseOrderId) {
        return purchaseOrderService.findPurchaseOrderById(purchaseOrderId);
    }

    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    public List<PurchaseOrder> findPurchaseOrdersByStatus(PurchaseStatus status) {
        return purchaseOrderService.findPurchaseOrdersByStatus(status);
    }

    /**
     * 添加采购项到采购单。
     *
     * @param purchaseOrderId 采购单号
     * @param skuCode         SKU编码
     * @param productName     产品名称
     * @param quantity        数量
     * @param unitPrice       单价
     * @return 更新后的采购单
     */
    public PurchaseOrder addPurchaseOrderItem(String purchaseOrderId, String skuCode, String productName, Integer quantity, BigDecimal unitPrice) {
        var purchaseOrder = purchaseOrderService.findPurchaseOrderById(purchaseOrderId);
        purchaseOrder.addItem(skuCode, productName, quantity, unitPrice);
        // 发布采购单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 批准采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 批准后的采购单
     */
    public PurchaseOrder approvePurchaseOrder(String purchaseOrderId) {
        PurchaseOrder purchaseOrder = purchaseOrderService.approvePurchaseOrder(purchaseOrderId);
        // 发布采购单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 拒绝采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 拒绝后的采购单
     */
    public PurchaseOrder rejectPurchaseOrder(String purchaseOrderId) {
        return purchaseOrderService.rejectPurchaseOrder(purchaseOrderId);
    }

    /**
     * 标记采购单为运输中。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    public PurchaseOrder markPurchaseOrderAsInTransit(String purchaseOrderId) {
        return purchaseOrderService.markPurchaseOrderAsInTransit(purchaseOrderId);
    }

    /**
     * 标记采购单为已送达。
     *
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    public PurchaseOrder markPurchaseOrderAsDelivered(String purchaseOrderId) {
        return purchaseOrderService.markPurchaseOrderAsDelivered(purchaseOrderId);
    }

    /**
     * 取消采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    public PurchaseOrder cancelPurchaseOrder(String purchaseOrderId) {
        return purchaseOrderService.cancelPurchaseOrder(purchaseOrderId);
    }
}
