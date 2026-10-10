package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.WarehouseJpaEntity;

/**
 * 仓库JPA仓库接口，用于操作仓库JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface WarehouseJpaRepository extends JpaRepository<WarehouseJpaEntity, String> {
    /**
     * 根据仓库编码查找仓库
     *
     * @param warehouseCode
     * @return
     */
    Optional<WarehouseJpaEntity> findByWarehouseCode(String warehouseCode);

    /**
     * 根据仓库名称查找仓库
     *
     * @param warehouseName
     * @return
     */
    Optional<WarehouseJpaEntity> findByWarehouseName(String warehouseName);

    /**
     * 查询所有可用的仓库
     *
     * @return
     */
    Collection<WarehouseJpaEntity> findByActiveTrue();

    /**
     * 统计租户下的仓库总数（首页概览用）
     */
    long countByTenantId(String tenantId);

    /**
     * 取租户下第一个仓库（按 ID 升序，稳定），供订单发货等链路解析出库仓
     */
    Optional<WarehouseJpaEntity> findFirstByTenantIdOrderByIdAsc(String tenantId);

    /**
     * 按租户列出全部仓库（按 ID 升序，稳定），供发货出库仓择仓遍历各仓库存
     */
    List<WarehouseJpaEntity> findAllByTenantIdOrderByIdAsc(String tenantId);

    /**
     * 按租户分页查询仓库
     */
    Page<WarehouseJpaEntity> findByTenantId(String tenantId, Pageable pageable);
}
