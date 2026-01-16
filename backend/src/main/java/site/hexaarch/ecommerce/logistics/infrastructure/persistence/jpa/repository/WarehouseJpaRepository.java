package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.WarehouseJpaEntity;

import java.util.Collection;
import java.util.Optional;

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
}
