package site.hexaarch.ecommerce.logistics.domain.finance.event;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务交易创建事件，当新财务交易创建时触发。
 *
 * @author kenyon
 */
@Getter
public class FinancialTransactionCreatedEvent extends BaseDomainEvent {
    private final String transactionId;
    private final String tenantId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String currency;
    private final String referenceId;
    private final String referenceType;
    private final LocalDateTime createdAt;

    /**
     * 构造函数。
     *
     * @param transactionId 交易ID
     * @param tenantId      租户ID
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param createdAt     创建时间
     */
    public FinancialTransactionCreatedEvent(
            String transactionId,
            String tenantId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.tenantId = tenantId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.createdAt = createdAt;
    }
}
