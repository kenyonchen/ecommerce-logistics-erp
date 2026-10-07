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
     * 创建租户，并在同一事务内创建该租户的管理员账号（自动授予 TENANT_ADMIN 角色）
     *
     * @param name           租户名称
     * @param description    租户描述
     * @param contactEmail   联系邮箱
     * @param contactPhone   联系电话
     * @param adminUsername  管理员用户名（全局唯一，用于登录）
     * @param adminPassword  管理员密码（明文，由实现方负责加密存储）
     * @param adminEmail     管理员邮箱，可为空
     * @param adminFirstName 管理员名，可为空
     * @param adminLastName  管理员姓，可为空
     * @return 创建的租户
     */
    Tenant createTenant(String name, String description, String contactEmail, String contactPhone,
                        String adminUsername, String adminPassword, String adminEmail,
                        String adminFirstName, String adminLastName);

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