package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductImportJobJpaEntity;

/**
 * 商品导入任务JPA仓库
 *
 * @author kenyon
 */
@Repository
public interface ProductImportJobJpaRepository
        extends JpaRepository<ProductImportJobJpaEntity, String>, JpaSpecificationExecutor<ProductImportJobJpaEntity> {
}
