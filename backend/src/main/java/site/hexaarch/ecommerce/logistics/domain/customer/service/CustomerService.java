package site.hexaarch.ecommerce.logistics.domain.customer.service;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.List;
import java.util.Optional;

/**
 * 客户领域服务接口，封装客户相关的业务逻辑。
 *
 * @author kenyon
 */
public interface CustomerService {
    /**
     * 创建客户。
     *
     * @param tenantId     租户ID
     * @param customerName 客户名称
     * @param email        客户邮箱（可空，非空时全局唯一）
     * @param phone        客户电话
     * @param address      客户地址
     * @param company      公司名（可空）
     * @return 创建的客户
     */
    Customer createCustomer(String tenantId, String customerName, String email, String phone, String address,
                            String company);

    /**
     * 从第三方平台导入创建客户：与手动创建的区别在于携带来源平台与平台买家ID（用于重复导入的幂等匹配）。
     *
     * @param tenantId        租户ID
     * @param customerName    客户名称
     * @param email           客户邮箱（可空，非空时全局唯一）
     * @param phone           客户电话
     * @param address         客户地址
     * @param company         公司名（可空）
     * @param sourcePlatform  来源平台
     * @param externalBuyerId 平台买家ID（可空）
     * @return 创建的客户
     */
    Customer createCustomerFromPlatform(String tenantId, String customerName, String email, String phone,
                                        String address, String company, String sourcePlatform, String externalBuyerId);

    /**
     * 根据客户ID查找客户，不存在时抛出 EntityNotFoundException。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    Customer findCustomerById(String customerId);

    /**
     * 根据客户ID查找客户（可为空）。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    Optional<Customer> findOptionalCustomerById(String customerId);

    /**
     * 条件分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Customer> searchCustomers(CustomerQuery query);

    /**
     * 更新客户资料。
     *
     * @param customerId   客户ID
     * @param customerName 客户名称
     * @param email        邮箱
     * @param phone        电话
     * @param address      地址
     * @param company      公司名（可空）
     * @return 更新后的客户
     */
    Customer updateCustomer(String customerId, String customerName, String email, String phone, String address,
                            String company);

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    List<Customer> findCustomersByStatus(CustomerStatus status);

    /**
     * 激活客户。
     *
     * @param customerId 客户ID
     * @return 激活后的客户
     */
    Customer activateCustomer(String customerId);

    /**
     * 停用客户。
     *
     * @param customerId 客户ID
     * @return 停用后的客户
     */
    Customer deactivateCustomer(String customerId);

    /**
     * 暂停客户。
     *
     * @param customerId 客户ID
     * @return 暂停后的客户
     */
    Customer suspendCustomer(String customerId);

    /**
     * 删除客户（软删除）。
     *
     * @param customerId 客户ID
     * @return 删除后的客户
     */
    Customer deleteCustomer(String customerId);
}
