package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 店铺仓库绑定本地仓库请求
 *
 * @author kenyon
 */
@Data
public class BindShopWarehouseRequest {
    private String tenantId;
    /**
     * 平台名称
     */
    private String platform;
    /**
     * 平台集成ID
     */
    private String integrationId;
    /**
     * 平台店铺编码
     */
    private String shopCode;
    /**
     * 平台店铺名称
     */
    private String shopName;
    /**
     * 平台仓库ID
     */
    private String platformWarehouseId;
    /**
     * 平台仓库名称
     */
    private String platformWarehouseName;
    /**
     * 平台仓库地址
     */
    private String address;
    /**
     * 是否默认发货仓库
     */
    private Boolean defaultWarehouse;
    /**
     * 绑定的本地仓库ID
     */
    private String localWarehouseId;
}
