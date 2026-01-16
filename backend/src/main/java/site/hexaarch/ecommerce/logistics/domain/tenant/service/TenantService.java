package site.hexaarch.ecommerce.logistics.domain.tenant.service;

import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;

import java.util.List;

/**
 * 租户服务接口
 *
 * @author kenyon
 */
public interface TenantService {
    /**
     * 创建租户
     *
     * @param name         租户名称
     * @param description  租户描述
     * @param contactEmail 联系邮箱
     * @param contactPhone 联系电话
     * @return 创建的租户
     */
    Tenant createTenant(String name, String description, String contactEmail, String contactPhone);

    /**
     * 根据ID获取租户
     *
     * @param id 租户ID
     * @return 租户
     */
    Tenant getTenantById(String id);

    /**
     * 获取所有租户
     *
     * @return 租户列表
     */
    List<Tenant> getAllTenants();

    /**
     * 更新租户信息
     *
     * @param id           租户ID
     * @param name         租户名称
     * @param description  租户描述
     * @param contactEmail 联系邮箱
     * @param contactPhone 联系电话
     * @return 更新后的租户
     */
    Tenant updateTenant(String id, String name, String description, String contactEmail, String contactPhone);

    /**
     * 激活租户
     *
     * @param id 租户ID
     * @return 激活后的租户
     */
    Tenant activateTenant(String id);

    /**
     * 暂停租户
     *
     * @param id 租户ID
     * @return 暂停后的租户
     */
    Tenant suspendTenant(String id);

    /**
     * 注销租户
     *
     * @param id 租户ID
     * @return 注销后的租户
     */
    Tenant deactivateTenant(String id);

    /**
     * 删除租户
     *
     * @param id 租户ID
     */
    void deleteTenant(String id);

    /**
     * 为租户创建管理员用户
     *
     * @param tenantId  租户ID
     * @param username  用户名
     * @param password  密码
     * @param email     邮箱
     * @param firstName 名
     * @param lastName  姓
     * @return 创建的用户
     */
    User createTenantAdmin(String tenantId, String username, String password, String email, String firstName, String lastName);
}