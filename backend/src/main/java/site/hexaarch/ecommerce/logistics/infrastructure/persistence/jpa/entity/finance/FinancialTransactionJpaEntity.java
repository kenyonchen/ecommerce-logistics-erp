package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务交易JPA实体，用于持久化财务交易信息。
 *
 * @author kenyon
 */
@Entity
@Table(name = "financial_transactions")
@Getter
@Setter
public class FinancialTransactionJpaEntity {
    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Column(name = "reference_type", nullable = false)
    private String referenceType;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
