package site.hexaarch.ecommerce.logistics.domain.customer.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.time.LocalDateTime;

/**
 * 客户状态变更事件，当客户状态发生变化时触发。
 *
 * @author kenyon
 */
@Getter
public class CustomerStatusChangedEvent extends BaseDomainEvent {
    private final String customerId;
    private final CustomerStatus oldStatus;
    private final CustomerStatus newStatus;
    private final LocalDateTime updatedAt;

    /**
     * 构造函数。
     *
     * @param customerId 客户ID
     * @param oldStatus  旧状态
     * @param newStatus  新状态
     * @param updatedAt  更新时间
     */
    public CustomerStatusChangedEvent(
            String customerId,
            CustomerStatus oldStatus,
            CustomerStatus newStatus,
            LocalDateTime updatedAt) {
        this.customerId = customerId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.updatedAt = updatedAt;
    }
}
