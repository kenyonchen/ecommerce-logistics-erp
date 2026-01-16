package site.hexaarch.ecommerce.logistics.domain.finance.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.finance.event.FinancialTransactionCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.event.FinancialTransactionStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务交易聚合根，负责管理财务交易的基本信息、状态和关联关系。
 *
 * @author kenyon
 */
@Getter
public class FinancialTransaction extends BaseAggregateRoot {
    private final String transactionId;
    private final String tenantId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String currency;
    private final String referenceId;
    private final String referenceType;
    private final String description;
    private final LocalDateTime createdAt;
    private TransactionStatus status;
    private LocalDateTime updatedAt;

    /**
     * 私有构造函数，只能通过create方法创建。
     */
    private FinancialTransaction(
            String transactionId,
            String tenantId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        this.transactionId = transactionId;
        this.tenantId = tenantId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.description = description;
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 创建财务交易。
     *
     * @param transactionId 交易ID
     * @param tenantId      租户ID
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @return 财务交易实例
     */
    public static FinancialTransaction create(
            String transactionId,
            String tenantId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        var transaction = new FinancialTransaction(
                transactionId,
                tenantId,
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description);
        // 发布财务交易创建事件
        transaction.registerDomainEvent(new FinancialTransactionCreatedEvent(
                transaction.getTransactionId(),
                transaction.getTenantId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getReferenceId(),
                transaction.getReferenceType(),
                transaction.getCreatedAt()
        ));
        return transaction;
    }

    // 手动添加builder方法
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 完成财务交易。
     */
    public void complete() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("交易状态不是待处理，无法完成");
        }
        var oldStatus = this.status;
        this.status = TransactionStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
        // 发布财务交易状态变更事件
        this.registerDomainEvent(new FinancialTransactionStatusChangedEvent(
                this.getTransactionId(),
                oldStatus,
                TransactionStatus.SUCCESS,
                this.getUpdatedAt()
        ));
    }

    /**
     * 失败财务交易。
     */
    public void fail() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("交易状态不是待处理，无法标记为失败");
        }
        var oldStatus = this.status;
        this.status = TransactionStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
        // 发布财务交易状态变更事件
        this.registerDomainEvent(new FinancialTransactionStatusChangedEvent(
                this.getTransactionId(),
                oldStatus,
                TransactionStatus.FAILED,
                this.getUpdatedAt()
        ));
    }

    /**
     * 取消财务交易。
     */
    public void cancel() {
        if (this.status == TransactionStatus.SUCCESS) {
            throw new IllegalArgumentException("交易已成功，无法取消");
        }
        var oldStatus = this.status;
        this.status = TransactionStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
        // 发布财务交易状态变更事件
        this.registerDomainEvent(new FinancialTransactionStatusChangedEvent(
                this.getTransactionId(),
                oldStatus,
                TransactionStatus.CANCELLED,
                this.getUpdatedAt()
        ));
    }

    // 手动添加toBuilder方法
    public Builder toBuilder() {
        return new Builder()
                .transactionId(this.transactionId)
                .tenantId(this.tenantId)
                .type(this.type)
                .amount(this.amount)
                .currency(this.currency)
                .referenceId(this.referenceId)
                .referenceType(this.referenceType)
                .description(this.description)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    public static class Builder {
        private String transactionId;
        private String tenantId;
        private TransactionType type;
        private BigDecimal amount;
        private String currency;
        private String referenceId;
        private String referenceType;
        private String description;
        private TransactionStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder referenceType(String referenceType) {
            this.referenceType = referenceType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public FinancialTransaction build() {
            var transaction = new FinancialTransaction(
                    transactionId,
                    tenantId,
                    type,
                    amount,
                    currency,
                    referenceId,
                    referenceType,
                    description
            );
            transaction.status = this.status != null ? this.status : TransactionStatus.PENDING;
            transaction.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return transaction;
        }
    }
}