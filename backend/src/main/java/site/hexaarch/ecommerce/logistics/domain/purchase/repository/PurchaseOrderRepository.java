package site.hexaarch.ecommerce.logistics.domain.purchase.repository;

import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.util.List;
import java.util.Optional;

/**
 * 采购单仓储接口，负责采购单的持久化和查询操作。
 *
 * @author kenyon
 */
public interface PurchaseOrderRepository {
    /**
     * 保存采购单。
     *
     * @param purchaseOrder 采购单实例
     */
    void save(PurchaseOrder purchaseOrder);

    /**
     * 根据采购单号查找采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 采购单实例，若不存在则返回Optional.empty()
     */
    Optional<PurchaseOrder> findById(String purchaseOrderId);

    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    List<PurchaseOrder> findByStatus(PurchaseStatus status);

    /**
     * 根据供应商ID查找采购单。
     *
     * @param supplierId 供应商ID
     * @return 采购单列表
     */
    List<PurchaseOrder> findBySupplierId(String supplierId);

    /**
     * 根据仓库ID查找采购单。
     *
     * @param warehouseId 仓库ID
     * @return 采购单列表
     */
    List<PurchaseOrder> findByWarehouseId(String warehouseId);
}
