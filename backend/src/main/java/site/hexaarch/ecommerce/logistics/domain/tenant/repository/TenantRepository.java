package site.hexaarch.ecommerce.logistics.domain.tenant.repository;

import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * 租户仓库接口
 *
 * @author kenyon
 */
public interface TenantRepository {
    /**
     * 保存租户
     *
     * @param tenant 租户聚合根
     * @return 保存后的租户
     */
    Tenant save(Tenant tenant);

    /**
     * 根据ID查找租户
     *
     * @param id 租户ID
     * @return 租户
     */
    Optional<Tenant> findById(String id);

    /**
     * 根据名称查找租户
     *
     * @param name 租户名称
     * @return 租户
     */
    Optional<Tenant> findByName(String name);

    /**
     * 根据联系邮箱查找租户
     *
     * @param email 联系邮箱
     * @return 租户
     */
    Optional<Tenant> findByContactEmail(String email);

    /**
     * 获取所有租户
     *
     * @return 租户列表
     */
    List<Tenant> findAll();

    /**
     * 删除租户
     *
     * @param id 租户ID
     */
    void deleteById(String id);
}