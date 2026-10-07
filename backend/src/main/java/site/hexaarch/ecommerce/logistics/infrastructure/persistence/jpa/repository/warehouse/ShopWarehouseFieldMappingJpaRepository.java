package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseFieldMappingJpaEntity;

/**
 * 店铺仓库字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface ShopWarehouseFieldMappingJpaRepository
        extends JpaRepository<ShopWarehouseFieldMappingJpaEntity, String> {

    List<ShopWarehouseFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    @Modifying
    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
