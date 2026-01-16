package site.hexaarch.ecommerce.logistics.application.service.customer;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerService;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;

/**
 * 客户应用服务，负责协调领域对象完成客户相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class CustomerApplicationService {
    private final CustomerService customerService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造函数，注入客户领域服务。
     *
     * @param customerService      客户领域服务
     * @param domainEventPublisher 领域事件发布服务
     */
    public CustomerApplicationService(CustomerService customerService, DomainEventPublisher domainEventPublisher) {
        this.customerService = customerService;
        this.domainEventPublisher = domainEventPublisher;
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
    public Customer createCustomer(String customerName, String email, String phone, String address) {
        Customer customer = customerService.createCustomer(customerName, email, phone, address);
        // 发布客户聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 根据客户ID查找客户。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    public Customer findCustomerById(String customerId) {
        return customerService.findCustomerById(customerId);
    }

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    public List<Customer> findCustomersByStatus(CustomerStatus status) {
        return customerService.findCustomersByStatus(status);
    }

    /**
     * 根据名称查找客户。
     *
     * @param customerName 客户名称
     * @return 客户列表
     */
    public List<Customer> findCustomersByName(String customerName) {
        return customerService.findCustomersByName(customerName);
    }

    /**
     * 激活客户。
     *
     * @param customerId 客户ID
     * @return 激活后的客户
     */
    public Customer activateCustomer(String customerId) {
        return customerService.activateCustomer(customerId);
    }

    /**
     * 停用客户。
     *
     * @param customerId 客户ID
     * @return 停用后的客户
     */
    public Customer deactivateCustomer(String customerId) {
        return customerService.deactivateCustomer(customerId);
    }

    /**
     * 暂停客户。
     *
     * @param customerId 客户ID
     * @return 暂停后的客户
     */
    public Customer suspendCustomer(String customerId) {
        return customerService.suspendCustomer(customerId);
    }

    /**
     * 删除客户。
     *
     * @param customerId 客户ID
     * @return 删除后的客户
     */
    public Customer deleteCustomer(String customerId) {
        return customerService.deleteCustomer(customerId);
    }
}
