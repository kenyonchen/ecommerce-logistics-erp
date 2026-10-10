package site.hexaarch.ecommerce.logistics.domain.purchase.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;

import java.math.BigDecimal;
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
     * 创建采购单并一次性写入明细行。
     *
     * @param tenantId     租户ID
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param warehouseId  仓库ID
     * @param items        明细行
     * @return 创建的采购单
     */
    @Override
    public PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId,
                                             List<PurchaseOrderItem> items) {
        requireText(tenantId, "租户ID(tenantId)不能为空");
        requireText(supplierId, "供应商ID(supplierId)不能为空");
        requireText(supplierName, "供应商名称(supplierName)不能为空");
        requireText(warehouseId, "收货仓库ID(warehouseId)不能为空");

        var purchaseOrderId = "PO-" + UUID.randomUUID().toString().substring(0, 8);
        var purchaseOrder = PurchaseOrder.create(purchaseOrderId, tenantId.trim(), supplierId.trim(),
                supplierName.trim(), warehouseId.trim());
        if (items != null) {
            // 走聚合的 addItem，复用 SKU 去重与金额汇总，不让外部构造绕过规则
            items.forEach(item -> purchaseOrder.addItem(item.getSkuCode(), item.getProductName(),
                    item.getQuantity(), item.getUnitPrice()));
        }
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 在指定租户内按采购单号查找采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    @Override
    public PurchaseOrder findPurchaseOrderById(String tenantId, String purchaseOrderId) {
        return purchaseOrderRepository.findById(tenantId, purchaseOrderId)
                .orElseThrow(() -> new EntityNotFoundException("采购单不存在: " + purchaseOrderId));
    }

    /**
     * 分页 + 条件查询采购单。
     *
     * @param query 查询条件
     * @param page  页码，从0开始
     * @param size  每页条数
     * @return 分页结果
     */
    @Override
    public PageResult<PurchaseOrder> searchPurchaseOrders(PurchaseQuery query, int page, int size) {
        return purchaseOrderRepository.search(query, page, size);
    }

    /**
     * 添加采购项并持久化采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param skuCode         SKU编码
     * @param productName     产品名称
     * @param quantity        数量
     * @param unitPrice       单价
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder addPurchaseOrderItem(String tenantId, String purchaseOrderId, String skuCode, String productName,
                                              Integer quantity, BigDecimal unitPrice) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.addItem(skuCode, productName, quantity, unitPrice);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 修改采购项的数量与单价。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param itemId          采购项ID
     * @param quantity        新数量
     * @param unitPrice       新单价
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder updatePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId,
                                                 Integer quantity, BigDecimal unitPrice) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.updateItem(itemId, quantity, unitPrice);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 删除采购项。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param itemId          采购项ID
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder removePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.removeItem(itemId);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 批准采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param comment         审批意见
     * @return 批准后的采购单
     */
    @Override
    public PurchaseOrder approvePurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String comment) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.approve(approvedBy, comment);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 拒绝采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param reason          拒绝原因
     * @return 拒绝后的采购单
     */
    @Override
    public PurchaseOrder rejectPurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String reason) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.reject(approvedBy, reason);
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为运输中。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder markPurchaseOrderAsInTransit(String tenantId, String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.markAsInTransit();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为已送达。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Override
    public PurchaseOrder markPurchaseOrderAsDelivered(String tenantId, String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.markAsDelivered();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 取消采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    @Override
    public PurchaseOrder cancelPurchaseOrder(String tenantId, String purchaseOrderId) {
        var purchaseOrder = findPurchaseOrderById(tenantId, purchaseOrderId);
        purchaseOrder.cancel();
        purchaseOrderRepository.save(purchaseOrder);
        return purchaseOrder;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
    }
}
