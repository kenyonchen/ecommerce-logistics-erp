package site.hexaarch.ecommerce.logistics.domain.customer.service;

import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.List;

/**
 * 客户领域服务，封装客户相关的业务逻辑。
 *
 * @author kenyon
 */
public interface CustomerService {
    /**
     * 创建客户。
     *
     * @param customerName 客户名称
     * @param email        客户邮箱
     * @param phone        客户电话
     * @param address      客户地址
     * @return 创建的客户
     */
    Customer createCustomer(String customerName, String email, String phone, String address);

    /**
     * 根据客户ID查找客户。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    Customer findCustomerById(String customerId);

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    List<Customer> findCustomersByStatus(CustomerStatus status);

    /**
     * 根据名称查找客户。
     *
     * @param customerName 客户名称
     * @return 客户列表
     */
    List<Customer> findCustomersByName(String customerName);

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
     * 删除客户。
     *
     * @param customerId 客户ID
     * @return 删除后的客户
     */
    Customer deleteCustomer(String customerId);
}
