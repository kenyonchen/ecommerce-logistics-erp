package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductFieldMappingJpaEntity;

/**
 * 第三方平台字段映射JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface ProductFieldMappingJpaRepository extends JpaRepository<ProductFieldMappingJpaEntity, String> {

    List<ProductFieldMappingJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    void deleteByTenantIdAndPlatform(String tenantId, String platform);
}
