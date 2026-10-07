package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.shop.ShopJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.shop.ShopRepositoryImpl;

/**
 * 店铺 JPA 仓库。
 * <p>
 * 这里声明的每一个查询方法都带 {@code tenantId} 条件。Spring Data 基类自带的
 * {@code findById}/{@code findAll} 属于「不可用」接口——只有 {@code ShopRepositoryImpl} 能看到本接口，
 * 而它一律不调用那些方法；对外契约是 {@code domain/shop/repository/ShopRepository}。
 * </p>
 *
 * @author kenyon
 */
public interface ShopJpaRepository extends JpaRepository<ShopJpaEntity, String> {

    Optional<ShopJpaEntity> findByIdAndTenantId(String id, String tenantId);

    List<ShopJpaEntity> findByTenantIdOrderByShopCodeAsc(String tenantId);

    List<ShopJpaEntity> findByTenantIdAndPlatform(String tenantId, String platform);

    Optional<ShopJpaEntity> findByTenantIdAndShopCode(String tenantId, String shopCode);

    boolean existsByTenantIdAndShopCode(String tenantId, String shopCode);

    boolean existsByTenantIdAndPlatformAndMarketplaceId(String tenantId, String platform, String marketplaceId);

    long countByTenantId(String tenantId);
}
