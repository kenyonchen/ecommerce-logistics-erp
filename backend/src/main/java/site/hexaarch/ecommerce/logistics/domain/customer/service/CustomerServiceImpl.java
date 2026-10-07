package site.hexaarch.ecommerce.logistics.domain.customer.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 客户领域服务的实现类，封装客户相关的业务逻辑。
 *
 * @author kenyon
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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
     * @param tenantId     租户ID
     * @param customerName 客户名称
     * @param email        客户邮箱（可空，非空时全局唯一）
     * @param phone        客户电话
     * @param address      客户地址
     * @return 创建的客户
     */
    @Override
    public Customer createCustomer(String tenantId, String customerName, String email, String phone, String address,
                                   String company) {
        if (!StringUtils.hasText(customerName)) {
            throw new ValidationException("客户名称不能为空");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && customerRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ValidationException("邮箱已被其他客户使用: " + normalizedEmail);
        }
        String customerId = generateCustomerId();
        var customer = Customer.create(tenantId, customerId, customerName.trim(), normalizedEmail,
                trimToNull(phone), trimToNull(address), trimToNull(company), null, null);
        return customerRepository.save(customer);
    }

    /**
     * 从第三方平台导入创建客户：邮箱规范化与查重逻辑与手动创建一致，
     * 额外记录来源平台与平台买家ID（幂等匹配键）。
     * 注意：该方法创建的 Customer 会挂载 CustomerCreatedEvent，由调用方负责发布。
     */
    @Override
    public Customer createCustomerFromPlatform(String tenantId, String customerName, String email, String phone,
                                               String address, String company, String sourcePlatform,
                                               String externalBuyerId) {
        if (!StringUtils.hasText(customerName)) {
            throw new ValidationException("客户名称不能为空");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && customerRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ValidationException("邮箱已被其他客户使用: " + normalizedEmail);
        }
        String customerId = generateCustomerId();
        var customer = Customer.create(tenantId, customerId, customerName.trim(), normalizedEmail,
                trimToNull(phone), trimToNull(address), trimToNull(company), sourcePlatform,
                trimToNull(externalBuyerId));
        return customerRepository.save(customer);
    }

    @Override
    public Customer findCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("客户不存在: " + customerId));
    }

    @Override
    public Optional<Customer> findOptionalCustomerById(String customerId) {
        return customerRepository.findById(customerId);
    }

    @Override
    public PageResult<Customer> searchCustomers(CustomerQuery query) {
        return customerRepository.findAll(query);
    }

    @Override
    public Customer updateCustomer(String customerId, String customerName, String email, String phone, String address,
                                   String company) {
        var customer = findCustomerById(customerId);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(customer.getEmail())) {
            customerRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
                if (!existing.getCustomerId().equals(customer.getCustomerId())) {
                    throw new ValidationException("邮箱已被其他客户使用: " + normalizedEmail);
                }
            });
        }
        customer.updateProfile(customerName, normalizedEmail, phone, address, company);
        return customerRepository.save(customer);
    }

    @Override
    public List<Customer> findCustomersByStatus(CustomerStatus status) {
        CustomerQuery query = new CustomerQuery(null, null, null, null, null, status, null, null, null, null,
                0, 200, null);
        return customerRepository.findAll(query).getContent();
    }

    @Override
    public Customer activateCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.activate();
        return customerRepository.save(customer);
    }

    @Override
    public Customer deactivateCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.deactivate();
        return customerRepository.save(customer);
    }

    @Override
    public Customer suspendCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.suspend();
        return customerRepository.save(customer);
    }

    @Override
    public Customer deleteCustomer(String customerId) {
        var customer = findCustomerById(customerId);
        customer.delete();
        return customerRepository.save(customer);
    }

    private String generateCustomerId() {
        return "CUST-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 邮箱规范化：空串归一为 null（避免唯一约束下多个空串冲突），非法格式直接拒绝
     */
    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        if (value == null) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new ValidationException("邮箱格式不正确: " + email);
        }
        return value.toLowerCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
