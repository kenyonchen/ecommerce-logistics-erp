package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseImportJobJpaEntity;

/**
 * 店铺仓库导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface ShopWarehouseImportJobJpaRepository
        extends JpaRepository<ShopWarehouseImportJobJpaEntity, String>,
        JpaSpecificationExecutor<ShopWarehouseImportJobJpaEntity> {
}
