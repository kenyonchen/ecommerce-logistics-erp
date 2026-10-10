package site.hexaarch.ecommerce.logistics.domain.purchase.service;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购单领域服务，封装采购单相关的业务逻辑。
 * <p>
 * 每个方法都要求 tenantId：采购单的读写必须限定在登录租户内。
 * </p>
 *
 * @author kenyon
 */
public interface PurchaseOrderService {
    /**
     * 创建采购单并一次性写入明细行。
     *
     * @param tenantId     租户ID
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param warehouseId  仓库ID
     * @param items        明细行（由 {@link PurchaseOrderItem#create} 构造，此处仅取字段做聚合内校验）
     * @return 创建的采购单
     */
    PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId,
                                      List<PurchaseOrderItem> items);

    /**
     * 在指定租户内按采购单号查找采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    PurchaseOrder findPurchaseOrderById(String tenantId, String purchaseOrderId);

    /**
     * 分页 + 条件查询采购单，按创建时间倒序。
     *
     * @param query 查询条件（含租户）
     * @param page  页码，从0开始
     * @param size  每页条数
     * @return 分页结果
     */
    PageResult<PurchaseOrder> searchPurchaseOrders(PurchaseQuery query, int page, int size);

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
    PurchaseOrder addPurchaseOrderItem(String tenantId, String purchaseOrderId, String skuCode, String productName,
                                       Integer quantity, BigDecimal unitPrice);

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
    PurchaseOrder updatePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId,
                                          Integer quantity, BigDecimal unitPrice);

    /**
     * 删除采购项。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param itemId          采购项ID
     * @return 更新后的采购单
     */
    PurchaseOrder removePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId);

    /**
     * 批准采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param comment         审批意见
     * @return 批准后的采购单
     */
    PurchaseOrder approvePurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String comment);

    /**
     * 拒绝采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param reason          拒绝原因
     * @return 拒绝后的采购单
     */
    PurchaseOrder rejectPurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String reason);

    /**
     * 标记采购单为运输中。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    PurchaseOrder markPurchaseOrderAsInTransit(String tenantId, String purchaseOrderId);

    /**
     * 标记采购单为已送达。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    PurchaseOrder markPurchaseOrderAsDelivered(String tenantId, String purchaseOrderId);

    /**
     * 取消采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    PurchaseOrder cancelPurchaseOrder(String tenantId, String purchaseOrderId);
}
