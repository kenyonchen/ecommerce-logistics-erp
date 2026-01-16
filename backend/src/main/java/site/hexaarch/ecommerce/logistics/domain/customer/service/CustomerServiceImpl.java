package site.hexaarch.ecommerce.logistics.domain.customer.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.List;
import java.util.UUID;

/**
 * 客户领域服务的实现类，封装客户相关的业务逻辑。
 *
 * @author kenyon
 */
@Service
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;

    /**
     * 构造函数，注入客户仓储。
     *
     * @param customerRepository 客户仓储
     */
    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * 创建客户。
     *
     * @param customerName 客户名称
     * @param email        客户邮箱
     * @param phone        客户电话
     * @param address      客户地址
     * @return 创建的客户
     */
    @Override
    public Customer createCustomer(String customerName, String email, String phone, String address) {
        var customerId = "CUST-" + UUID.randomUUID().toString().substring(0, 8);
        var customer = Customer.create("default-tenant", customerId, customerName, email, phone, address);
        customerRepository.save(customer);
        return customer;
    }

    /**
     * 根据客户ID查找客户。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    @Override
    public Customer findCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在: " + customerId));
    }

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    @Override
    public List<Customer> findCustomersByStatus(CustomerStatus status) {
        return customerRepository.findByStatus(status);
    }

    /**
     * 根据名称查找客户。
     *
     * @param customerName 客户名称
     * @return 客户列表
     */
    @Override
    public List<Customer> findCustomersByName(String customerName) {
        return customerRepository.findByName(customerName);
    }

    /**
     * 激活客户。
     *
     * @param customerId 客户ID
     * @return 激活后的客户
     */
    @Override
    public Customer activateCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.activate();
        customerRepository.save(customer);
        return customer;
    }

    /**
     * 停用客户。
     *
     * @param customerId 客户ID
     * @return 停用后的客户
     */
    @Override
    public Customer deactivateCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.deactivate();
        customerRepository.save(customer);
        return customer;
    }

    /**
     * 暂停客户。
     *
     * @param customerId 客户ID
     * @return 暂停后的客户
     */
    @Override
    public Customer suspendCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.suspend();
        customerRepository.save(customer);
        return customer;
    }

    /**
     * 删除客户。
     *
     * @param customerId 客户ID
     * @return 删除后的客户
     */
    @Override
    public Customer deleteCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.delete();
        customerRepository.save(customer);
        return customer;
    }
}
