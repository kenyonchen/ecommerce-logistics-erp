package site.hexaarch.ecommerce.logistics.domain.finance.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;

import java.time.LocalDateTime;

/**
 * 财务交易状态变更事件，当财务交易状态发生变化时触发。
 *
 * @author kenyon
 */
@Getter
public class FinancialTransactionStatusChangedEvent extends BaseDomainEvent {
    private final String transactionId;
    private final TransactionStatus oldStatus;
    private final TransactionStatus newStatus;
    private final LocalDateTime updatedAt;

    /**
     * 构造函数。
     *
     * @param transactionId 交易ID
     * @param oldStatus     旧状态
     * @param newStatus     新状态
     * @param updatedAt     更新时间
     */
    public FinancialTransactionStatusChangedEvent(
            String transactionId,
            TransactionStatus oldStatus,
            TransactionStatus newStatus,
            LocalDateTime updatedAt) {
        this.transactionId = transactionId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.updatedAt = updatedAt;
    }
}
