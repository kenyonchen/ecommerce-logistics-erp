package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.ProductJpaEntity;

import java.util.Optional;

/**
 * 产品JPA仓库接口，用于操作产品JPA实体。
 *
 * @author kenyon
 */
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, String> {
    /**
     * 根据产品名称查找产品
     *
     * @param name 产品名称
     * @return 产品实体
     */
    Optional<ProductJpaEntity> findByName(String name);

    /**
     * 根据SKU编码查找产品
     *
     * @param skuCode SKU编码
     * @return 产品实体
     */
    @Query("SELECT p FROM ProductJpaEntity p JOIN p.skus s WHERE s.skuCode = :skuCode")
    Optional<ProductJpaEntity> findBySkuCode(@Param("skuCode") String skuCode);
}