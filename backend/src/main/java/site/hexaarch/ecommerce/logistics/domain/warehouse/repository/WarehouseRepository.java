package site.hexaarch.ecommerce.logistics.domain.warehouse.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;

import java.util.List;
import java.util.Optional;

/**
 * 仓库仓储接口，负责持久化仓库聚合和提供仓库的访问方法。
 *
 * @author kenyon
 */
public interface WarehouseRepository {
    /**
     * 保存仓库。
     *
     * @param warehouse 仓库聚合
     * @return 保存后的仓库聚合
     */
    Warehouse save(Warehouse warehouse);

    /**
     * 根据ID查找仓库。
     *
     * @param id 仓库ID
     * @return 仓库聚合，如果不存在则返回Optional.empty()
     */
    Optional<Warehouse> findById(String id);

    Optional<Warehouse> findByWarehouseCode(String warehouseCode);

    Optional<Warehouse> findByWarehouseName(String warehouseName);

    /**
     * 查找所有激活的仓库。
     *
     * @return 仓库聚合列表
     */
    List<Warehouse> findActiveWarehouses();

    /**
     * 查找所有仓库。
     *
     * @return 仓库聚合列表
     */
    List<Warehouse> findAll();

    /**
     * 取租户下第一个仓库（按 ID 升序，稳定）。
     *
     * @param tenantId 租户ID
     * @return 仓库聚合
     */
    Optional<Warehouse> findFirstByTenantId(String tenantId);

    /**
     * 列出租户下的全部仓库（按 ID 升序，稳定），供发货出库仓择仓遍历各仓库存。
     *
     * @param tenantId 租户ID
     * @return 仓库聚合列表
     */
    List<Warehouse> listByTenantId(String tenantId);

    /**
     * 按租户分页查询仓库（tenantId 为空时不过滤）。
     *
     * @param tenantId 租户ID（可空）
     * @param page     页码，从0开始
     * @param size     每页条数
     * @return 分页结果
     */
    PageResult<Warehouse> search(String tenantId, int page, int size);

    /**
     * 删除仓库。
     *
     * @param id 仓库ID
     */
    void delete(String id);
}