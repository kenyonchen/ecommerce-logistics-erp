package site.hexaarch.ecommerce.logistics.domain.customer.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.customer.event.CustomerCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.customer.event.CustomerStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.time.LocalDateTime;

/**
 * 客户聚合根，负责管理客户的基本信息、状态和关联关系。
 *
 * @author kenyon
 */
@Getter
public class Customer extends BaseAggregateRoot {
    private final String tenantId;
    private final String customerId;
    private final String customerName;
    private final String email;
    private final String phone;
    private final String address;
    private final LocalDateTime createdAt;
    private CustomerStatus status;
    private LocalDateTime updatedAt;

    /**
     * 私有构造函数，只能通过create方法创建。
     */
    private Customer(
            String tenantId,
            String customerId,
            String customerName,
            String email,
            String phone,
            String address) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.status = CustomerStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 创建客户。
     *
     * @param tenantId     租户ID
     * @param customerId   客户ID
     * @param customerName 客户名称
     * @param email        客户邮箱
     * @param phone        客户电话
     * @param address      客户地址
     * @return 客户实例
     */
    public static Customer create(
            String tenantId,
            String customerId,
            String customerName,
            String email,
            String phone,
            String address) {
        var customer = new Customer(tenantId, customerId, customerName, email, phone, address);
        // 发布客户创建事件
        customer.registerDomainEvent(new CustomerCreatedEvent(
                customer.getCustomerId(),
                customer.getCustomerName(),
                customer.getEmail(),
                customer.getCreatedAt()
        ));
        return customer;
    }

    /**
     * 激活客户。
     */
    public void activate() {
        if (this.status == CustomerStatus.ACTIVE) {
            return; // 已经是活跃状态，不需要改变
        }
        var oldStatus = this.status;
        this.status = CustomerStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
        // 发布客户状态变更事件
        this.registerDomainEvent(new CustomerStatusChangedEvent(
                this.getCustomerId(),
                oldStatus,
                CustomerStatus.ACTIVE,
                this.getUpdatedAt()
        ));
    }

    /**
     * 停用客户。
     */
    public void deactivate() {
        if (this.status == CustomerStatus.INACTIVE) {
            return; // 已经是非活跃状态，不需要改变
        }
        var oldStatus = this.status;
        this.status = CustomerStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
        // 发布客户状态变更事件
        this.registerDomainEvent(new CustomerStatusChangedEvent(
                this.getCustomerId(),
                oldStatus,
                CustomerStatus.INACTIVE,
                this.getUpdatedAt()
        ));
    }

    /**
     * 暂停客户。
     */
    public void suspend() {
        if (this.status == CustomerStatus.SUSPENDED) {
            return; // 已经是暂停状态，不需要改变
        }
        var oldStatus = this.status;
        this.status = CustomerStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
        // 发布客户状态变更事件
        this.registerDomainEvent(new CustomerStatusChangedEvent(
                this.getCustomerId(),
                oldStatus,
                CustomerStatus.SUSPENDED,
                this.getUpdatedAt()
        ));
    }


    /**
     * 删除客户。
     */
    public void delete() {
        if (this.status == CustomerStatus.DELETED) {
            return; // 已经是删除状态，不需要改变
        }
        var oldStatus = this.status;
        this.status = CustomerStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
        // 发布客户状态变更事件
        this.registerDomainEvent(new CustomerStatusChangedEvent(
                this.getCustomerId(),
                oldStatus,
                CustomerStatus.DELETED,
                this.getUpdatedAt()
        ));
    }

    /**
     * 获取租户ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取客户ID
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * 获取客户名称
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * 获取客户邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 获取客户电话
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 获取客户地址
     */
    public String getAddress() {
        return address;
    }

    /**
     * 获取客户状态
     */
    public CustomerStatus getStatus() {
        return status;
    }

    /**
     * 获取创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
