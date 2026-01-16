package site.hexaarch.ecommerce.logistics.domain.customer.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;

import java.time.LocalDateTime;

/**
 * 客户创建事件，当新客户创建时触发。
 *
 * @author kenyon
 */
@Getter
public class CustomerCreatedEvent extends BaseDomainEvent {
    private final String customerId;
    private final String customerName;
    private final String email;
    private final LocalDateTime createdAt;

    /**
     * 构造函数。
     *
     * @param customerId   客户ID
     * @param customerName 客户名称
     * @param email        客户邮箱
     * @param createdAt    创建时间
     */
    public CustomerCreatedEvent(
            String customerId,
            String customerName,
            String email,
            LocalDateTime createdAt) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.email = email;
        this.createdAt = createdAt;
    }
}
