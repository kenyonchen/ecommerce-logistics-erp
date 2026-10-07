package site.hexaarch.ecommerce.logistics.domain.purchase.repository;

import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单列表查询条件。
 *
 * @param tenantId       租户ID（必填，为空表示跨租户查询，仅平台管理员路径会出现）
 * @param purchaseOrderId 采购单号（模糊）
 * @param supplierId     供应商ID
 * @param supplierName   供应商名称（模糊）
 * @param warehouseId    收货仓库ID
 * @param status         采购状态
 * @param minTotalAmount 总金额下限（含）
 * @param createdFrom    创建时间起（含）
 * @param createdTo      创建时间止（含）
 * @author kenyon
 */
public record PurchaseQuery(String tenantId,
                            String purchaseOrderId,
                            String supplierId,
                            String supplierName,
                            String warehouseId,
                            PurchaseStatus status,
                            BigDecimal minTotalAmount,
                            LocalDateTime createdFrom,
                            LocalDateTime createdTo) {

    public static PurchaseQuery of(String tenantId) {
        return new PurchaseQuery(tenantId, null, null, null, null, null, null, null, null);
    }
}
