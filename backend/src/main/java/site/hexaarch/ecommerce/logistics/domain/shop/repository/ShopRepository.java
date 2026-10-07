package site.hexaarch.ecommerce.logistics.domain.shop.repository;

import site.hexaarch.ecommerce.logistics.domain.shop.aggregate.Shop;

import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.interfaces.controller.platform.PlatformIntegrationController;

/**
 * 店铺仓储接口。
 * <p>
 * <b>本接口刻意不提供任何无租户条件的查询</b>：没有裸 {@code findById(id)}，也没有 {@code findAll()}。
 * 这是 ADR-0013 §4.3 锁 2 的落点——让「忘了过滤租户」在编译期就不可表达。
 * </p>
 * <p>
 * 对照反面例子：{@code PlatformIntegrationRepository} 同时提供 {@code findById(UUID)} 与
 * {@code findAll()}，二者都不带租户条件，于是 {@code PlatformIntegrationController} 的列表接口
 * 可以直接委托到无过滤的服务上（ISS-050 的形状）。店铺域不得复制那个形状。
 * 平台管理员也不走「无租户全量」这条路：其店铺读接口同样必须显式指定 {@code tenantId}（§4.4）。
 * </p>
 *
 * @author kenyon
 */
public interface ShopRepository {

    /**
     * 保存。新店铺由 {@code Shop.create} 赋 id，更新时按 id 合并。
     */
    Shop save(Shop shop);

    /**
     * 按 id 与本租户定位。跨租户读取在本层就取不到，不依赖上层校验兜底。
     */
    Optional<Shop> findByIdAndTenantId(String id, String tenantId);

    /**
     * 本租户的店铺列表。
     */
    List<Shop> findByTenantId(String tenantId);

    List<Shop> findByTenantIdAndPlatform(String tenantId, String platform);

    /**
     * 按店铺编码定位（租户内唯一键的业务读入口）。
     */
    Optional<Shop> findByTenantIdAndShopCode(String tenantId, String shopCode);

    /**
     * 店铺编码是否已被本租户占用。
     *
     * @param excludeId 改编码场景下排除自身；新建场景传 {@code null}
     */
    boolean existsByTenantIdAndShopCode(String tenantId, String shopCode, String excludeId);

    /**
     * 同一平台下该站点是否已被绑定（辅助唯一键 {@code uk_shops_marketplace} 的业务预检，
     * 让重复绑定报出指向字段的错误而不是底层约束冲突）。
     */
    boolean existsByTenantIdAndPlatformAndMarketplaceId(String tenantId, String platform, String marketplaceId);

    long countByTenantId(String tenantId);

    /**
     * 按 id 与本租户删除。
     */
    void deleteByIdAndTenantId(String id, String tenantId);
}
