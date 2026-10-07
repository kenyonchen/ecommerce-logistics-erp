package site.hexaarch.ecommerce.logistics.domain.warehouse.repository;

import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;

import java.util.List;
import java.util.Optional;

/**
 * 店铺仓库仓储接口
 *
 * @author kenyon
 */
public interface ShopWarehouseRepository {

    ShopWarehouse save(ShopWarehouse shopWarehouse);

    Optional<ShopWarehouse> findById(String id);

    /**
     * 按租户+平台+平台仓库ID定位（唯一键）
     */
    Optional<ShopWarehouse> findByTenantIdAndPlatformAndPlatformWarehouseId(
            String tenantId, String platform, String platformWarehouseId);

    List<ShopWarehouse> findByTenantId(String tenantId);

    List<ShopWarehouse> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 查询绑定到指定本地仓库的店铺仓库
     */
    List<ShopWarehouse> findByLocalWarehouseId(String localWarehouseId);

    void delete(String id);
}
