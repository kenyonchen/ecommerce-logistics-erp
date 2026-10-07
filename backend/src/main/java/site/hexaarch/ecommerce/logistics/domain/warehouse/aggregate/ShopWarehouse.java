package site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 店铺仓库聚合根：第三方电商平台店铺下的仓库（云仓/FBA仓/海外仓等）与本系统本地仓库的绑定关系。
 * <p>
 * 一个平台店铺仓库绑定一个本地仓库；同步、库存推送、订单履约时依据该绑定关系路由。
 * </p>
 *
 * @author kenyon
 */
@Getter
public class ShopWarehouse {

    private String id;
    private String tenantId;
    /**
     * 平台名称（PlatformName 的名称字符串），或 FILE 表示文件导入
     */
    private String platform;
    /**
     * 关联的平台集成ID
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
     * 平台侧仓库ID
     */
    private String platformWarehouseId;
    /**
     * 平台侧仓库名称
     */
    private String platformWarehouseName;
    /**
     * 绑定的本地仓库ID
     */
    private String localWarehouseId;
    /**
     * 绑定的本地仓库编码
     */
    private String localWarehouseCode;
    /**
     * 平台仓库地址
     */
    private String address;
    /**
     * 是否为该店铺的默认发货仓库
     */
    private boolean defaultWarehouse;
    /**
     * 是否启用
     */
    private boolean active;
    /**
     * 最近同步时间
     */
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ShopWarehouse() {
    }

    /**
     * 创建（或更新）店铺仓库绑定关系
     */
    public static ShopWarehouse create(String tenantId, String platform, String integrationId,
                                       String shopCode, String shopName,
                                       String platformWarehouseId, String platformWarehouseName,
                                       String address, boolean defaultWarehouse) {
        if (platformWarehouseId == null || platformWarehouseId.isBlank()) {
            throw new ValidationException("平台仓库ID不能为空");
        }
        if (platform == null || platform.isBlank()) {
            throw new ValidationException("平台名称不能为空");
        }
        ShopWarehouse shopWarehouse = new ShopWarehouse();
        shopWarehouse.id = UUID.randomUUID().toString();
        shopWarehouse.tenantId = tenantId;
        shopWarehouse.platform = platform.trim().toUpperCase();
        shopWarehouse.integrationId = integrationId;
        shopWarehouse.shopCode = shopCode;
        shopWarehouse.shopName = shopName;
        shopWarehouse.platformWarehouseId = platformWarehouseId.trim();
        shopWarehouse.platformWarehouseName = platformWarehouseName;
        shopWarehouse.address = address;
        shopWarehouse.defaultWarehouse = defaultWarehouse;
        shopWarehouse.active = true;
        shopWarehouse.lastSyncedAt = LocalDateTime.now();
        shopWarehouse.createdAt = LocalDateTime.now();
        shopWarehouse.updatedAt = LocalDateTime.now();
        return shopWarehouse;
    }

    /**
     * 依据持久化结果还原实例
     */
    public static ShopWarehouse restore(String id, String tenantId, String platform, String integrationId,
                                        String shopCode, String shopName, String platformWarehouseId,
                                        String platformWarehouseName, String localWarehouseId,
                                        String localWarehouseCode, String address, boolean defaultWarehouse,
                                        boolean active, LocalDateTime lastSyncedAt,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        ShopWarehouse shopWarehouse = new ShopWarehouse();
        shopWarehouse.id = id;
        shopWarehouse.tenantId = tenantId;
        shopWarehouse.platform = platform;
        shopWarehouse.integrationId = integrationId;
        shopWarehouse.shopCode = shopCode;
        shopWarehouse.shopName = shopName;
        shopWarehouse.platformWarehouseId = platformWarehouseId;
        shopWarehouse.platformWarehouseName = platformWarehouseName;
        shopWarehouse.localWarehouseId = localWarehouseId;
        shopWarehouse.localWarehouseCode = localWarehouseCode;
        shopWarehouse.address = address;
        shopWarehouse.defaultWarehouse = defaultWarehouse;
        shopWarehouse.active = active;
        shopWarehouse.lastSyncedAt = lastSyncedAt;
        shopWarehouse.createdAt = createdAt;
        shopWarehouse.updatedAt = updatedAt;
        return shopWarehouse;
    }

    /**
     * 绑定到本地仓库
     */
    public void bindToLocalWarehouse(String localWarehouseId, String localWarehouseCode) {
        if (localWarehouseId == null || localWarehouseId.isBlank()) {
            throw new ValidationException("本地仓库ID不能为空");
        }
        this.localWarehouseId = localWarehouseId;
        this.localWarehouseCode = localWarehouseCode;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 解除与本地仓库的绑定
     */
    public void unbind() {
        this.localWarehouseId = null;
        this.localWarehouseCode = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 是否已绑定本地仓库
     */
    public boolean isBound() {
        return localWarehouseId != null && !localWarehouseId.isBlank();
    }

    /**
     * 更新平台侧信息（同步时使用）
     */
    public void updateFromPlatform(String shopName, String platformWarehouseName, String address,
                                   Boolean defaultWarehouse, String integrationId) {
        if (shopName != null) {
            this.shopName = shopName;
        }
        if (platformWarehouseName != null) {
            this.platformWarehouseName = platformWarehouseName;
        }
        if (address != null) {
            this.address = address;
        }
        if (defaultWarehouse != null) {
            this.defaultWarehouse = defaultWarehouse;
        }
        if (integrationId != null) {
            this.integrationId = integrationId;
        }
        this.lastSyncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 启用/停用
     */
    public void changeActive(boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }
}
