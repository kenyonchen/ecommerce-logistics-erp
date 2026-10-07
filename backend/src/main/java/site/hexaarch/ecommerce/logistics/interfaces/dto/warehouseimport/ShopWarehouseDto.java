package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 店铺仓库查询/绑定结果DTO
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseDto {
    private String id;
    private String tenantId;
    private String platform;
    private String integrationId;
    private String shopCode;
    private String shopName;
    private String platformWarehouseId;
    private String platformWarehouseName;
    private String localWarehouseId;
    private String localWarehouseCode;
    private String localWarehouseName;
    private String address;
    private Boolean defaultWarehouse;
    private Boolean active;
    private Boolean bound;
    private String lastSyncedAt;
    private String createdAt;
    private String updatedAt;
}
