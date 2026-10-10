package site.hexaarch.ecommerce.logistics.application.service.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerService;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;
import java.util.Optional;

/**
 * 客户应用服务，负责协调领域对象完成客户相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class CustomerApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";

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
     * @param tenantId     租户ID
     * @param customerName 客户名称
     * @param email        客户邮箱
     * @param phone        客户电话
     * @param address      客户地址
     * @param company      公司名（可空）
     * @return 创建的客户
     */
    @Transactional
    public Customer createCustomer(String tenantId, String customerName, String email, String phone, String address,
                                   String company) {
        Customer customer = customerService.createCustomer(
                TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID),
                customerName, email, phone, address, company);
        // 发布客户聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 从第三方平台导入创建客户。
     *
     * @param tenantId        租户ID
     * @param customerName    客户名称
     * @param email           客户邮箱
     * @param phone           客户电话
     * @param address         客户地址
     * @param company         公司名（可空）
     * @param sourcePlatform  来源平台
     * @param externalBuyerId 平台买家ID（可空）
     * @return 创建的客户
     */
    @Transactional
    public Customer createCustomerFromPlatform(String tenantId, String customerName, String email, String phone,
                                               String address, String company, String sourcePlatform,
                                               String externalBuyerId) {
        Customer customer = customerService.createCustomerFromPlatform(
                TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID),
                customerName, email, phone, address, company,
                sourcePlatform, externalBuyerId);
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
        Customer customer = customerService.findCustomerById(customerId);
        // 按ID直读客户不校验租户，先收敛归属（ISS-050）
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        return customer;
    }

    /**
     * 根据客户ID查找客户（可为空）。
     *
     * @param customerId 客户ID
     * @return 客户实例
     */
    public Optional<Customer> findOptionalCustomerById(String customerId) {
        Optional<Customer> customer = customerService.findOptionalCustomerById(customerId);
        // 命中其他租户客户按不存在处理（ISS-050）
        customer.ifPresent(c -> TenantAccessGuard.requireOwned("Customer", c.getTenantId(), customerId));
        return customer;
    }

    /**
     * 条件分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<Customer> searchCustomers(CustomerQuery query) {
        // 列表按登录租户收敛：tenantId 为空仓储层不加租户条件，会返回全平台客户（ISS-050）
        CustomerQuery scopedQuery = new CustomerQuery(TenantAccessGuard.scopedTenantId(query.tenantId()),
                query.customerId(), query.customerName(), query.email(), query.phone(), query.status(),
                query.sourcePlatform(), query.keyword(), query.createdFrom(), query.createdTo(),
                query.page(), query.size(), query.sort());
        return customerService.searchCustomers(scopedQuery);
    }

    /**
     * 更新客户资料。
     *
     * @param customerId   客户ID
     * @param customerName 客户名称
     * @param email        邮箱
     * @param phone        电话
     * @param address      地址
     * @return 更新后的客户
     */
    @Transactional
    public Customer updateCustomer(String customerId, String customerName, String email, String phone, String address,
                                   String company) {
        Customer customer = customerService.updateCustomer(customerId, customerName, email, phone, address, company);
        // 领域服务按ID直改客户，返回后校验归属，跨租户改动随事务回滚（ISS-050）
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    public List<Customer> findCustomersByStatus(CustomerStatus status) {
        // 领域服务构造的查询不带租户条件，按登录租户收敛（ISS-050）
        return withinCurrentTenant(customerService.findCustomersByStatus(status));
    }

    /**
     * 激活客户。
     *
     * @param customerId 客户ID
     * @return 激活后的客户
     */
    @Transactional
    public Customer activateCustomer(String customerId) {
        Customer customer = customerService.activateCustomer(customerId);
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 停用客户。
     *
     * @param customerId 客户ID
     * @return 停用后的客户
     */
    @Transactional
    public Customer deactivateCustomer(String customerId) {
        Customer customer = customerService.deactivateCustomer(customerId);
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 暂停客户。
     *
     * @param customerId 客户ID
     * @return 暂停后的客户
     */
    @Transactional
    public Customer suspendCustomer(String customerId) {
        Customer customer = customerService.suspendCustomer(customerId);
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 删除客户（软删除）。
     *
     * @param customerId 客户ID
     * @return 删除后的客户
     */
    @Transactional
    public Customer deleteCustomer(String customerId) {
        Customer customer = customerService.deleteCustomer(customerId);
        TenantAccessGuard.requireOwned("Customer", customer.getTenantId(), customerId);
        domainEventPublisher.publishEventsFrom(customer);
        return customer;
    }

    /**
     * 列表按登录租户收敛；平台管理员或无租户上下文时不过滤（ISS-050）。
     */
    private List<Customer> withinCurrentTenant(List<Customer> customers) {
        String tenantId = TenantAccessGuard.currentTenantId();
        if (tenantId == null) {
            return customers;
        }
        return customers.stream()
                .filter(customer -> tenantId.equals(customer.getTenantId()))
                .toList();
    }
}
