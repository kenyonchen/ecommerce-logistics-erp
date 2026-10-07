package site.hexaarch.ecommerce.logistics.domain.customer.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.customer.event.CustomerCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.customer.event.CustomerStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 客户聚合根，负责管理客户（跨境场景即平台买家）的基本信息、状态和来源平台关联。
 *
 * @author kenyon
 */
@Getter
public class Customer extends BaseAggregateRoot<Object> {
    private final String tenantId;
    private final String customerId;
    private String customerName;
    private String email;
    private String phone;
    private String address;
    /**
     * 公司名（跨境B2B买家常带公司），平台导入可携带
     */
    private String company;
    /**
     * 来源平台（第三方电商平台名称），手动创建时为空；FILE 表示文件导入
     */
    private final String sourcePlatform;
    /**
     * 平台侧买家ID，与 sourcePlatform 一起用于重复导入的幂等匹配
     */
    private final String externalBuyerId;
    private final LocalDateTime createdAt;
    private CustomerStatus status;
    private LocalDateTime updatedAt;

    private Customer(
            String tenantId,
            String customerId,
            String customerName,
            String email,
            String phone,
            String address,
            String company,
            String sourcePlatform,
            String externalBuyerId,
            CustomerStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.company = company;
        this.sourcePlatform = sourcePlatform;
        this.externalBuyerId = externalBuyerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建客户。
     *
     * @param tenantId        租户ID
     * @param customerId      客户ID
     * @param customerName    客户名称
     * @param email           客户邮箱
     * @param phone           客户电话
     * @param address         客户地址
     * @param company         公司名（可空）
     * @param sourcePlatform  来源平台（可空）
     * @param externalBuyerId 平台买家ID（可空）
     * @return 客户实例
     */
    public static Customer create(
            String tenantId,
            String customerId,
            String customerName,
            String email,
            String phone,
            String address,
            String company,
            String sourcePlatform,
            String externalBuyerId) {
        LocalDateTime now = LocalDateTime.now();
        var customer = new Customer(tenantId, customerId, customerName, email, phone, address, company,
                sourcePlatform, externalBuyerId, CustomerStatus.ACTIVE, now, now);
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
     * 依据持久化结果还原客户实例（仓储读取时使用，不注册领域事件、不篡改时间戳）
     */
    public static Customer restore(
            String tenantId,
            String customerId,
            String customerName,
            String email,
            String phone,
            String address,
            String company,
            String sourcePlatform,
            String externalBuyerId,
            CustomerStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new Customer(tenantId, customerId, customerName, email, phone, address, company,
                sourcePlatform, externalBuyerId,
                status == null ? CustomerStatus.ACTIVE : status, createdAt, updatedAt);
    }

    /**
     * 激活客户。
     */
    public void activate() {
        changeStatus(CustomerStatus.ACTIVE);
    }

    /**
     * 停用客户。
     */
    public void deactivate() {
        changeStatus(CustomerStatus.INACTIVE);
    }

    /**
     * 暂停客户。
     */
    public void suspend() {
        changeStatus(CustomerStatus.SUSPENDED);
    }

    /**
     * 删除客户（软删除：置为 DELETED 状态）。
     */
    public void delete() {
        changeStatus(CustomerStatus.DELETED);
    }

    /**
     * 更新客户资料：传入的非空值覆盖，空值保持原值。
     */
    public void updateProfile(String customerName, String email, String phone, String address, String company) {
        assertNotDeleted();
        if (StringUtils.hasText(customerName)) {
            this.customerName = customerName.trim();
        }
        if (StringUtils.hasText(email)) {
            this.email = email.trim();
        }
        if (phone != null) {
            this.phone = phone.trim();
        }
        if (address != null) {
            this.address = address.trim();
        }
        if (company != null) {
            this.company = company.trim();
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 导入覆盖更新：补齐缺失的联系信息并刷新时间戳（不覆盖已有值）。
     */
    public void mergeImportedProfile(String customerName, String email, String phone, String address, String company) {
        assertNotDeleted();
        if (!StringUtils.hasText(this.customerName) && StringUtils.hasText(customerName)) {
            this.customerName = customerName.trim();
        }
        if (!StringUtils.hasText(this.email) && StringUtils.hasText(email)) {
            this.email = email.trim();
        }
        if (!StringUtils.hasText(this.phone) && StringUtils.hasText(phone)) {
            this.phone = phone.trim();
        }
        if (!StringUtils.hasText(this.address) && StringUtils.hasText(address)) {
            this.address = address.trim();
        }
        if (!StringUtils.hasText(this.company) && StringUtils.hasText(company)) {
            this.company = company.trim();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isDeleted() {
        return status.isDeleted();
    }

    private void assertNotDeleted() {
        if (this.status == CustomerStatus.DELETED) {
            throw new InvalidOperationException("客户已删除，不允许修改: " + this.customerId);
        }
    }

    private void changeStatus(CustomerStatus newStatus) {
        if (this.status == newStatus) {
            return; // 状态未变化
        }
        if (this.status == CustomerStatus.DELETED) {
            throw new InvalidOperationException("客户已删除，不允许再变更状态: " + this.customerId);
        }
        var oldStatus = this.status;
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        // 发布客户状态变更事件
        this.registerDomainEvent(new CustomerStatusChangedEvent(
                this.getCustomerId(),
                oldStatus,
                newStatus,
                this.getUpdatedAt()
        ));
    }
}
