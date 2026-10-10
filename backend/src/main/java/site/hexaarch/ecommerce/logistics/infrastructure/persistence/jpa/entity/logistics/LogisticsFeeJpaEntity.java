package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 物流费用JPA实体，用于持久化物流费用值对象。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_fees")
@Getter
@Setter
public class LogisticsFeeJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "base_fee", precision = 19, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "weight_fee", precision = 19, scale = 2)
    private BigDecimal weightFee;

    @Column(name = "total_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "currency", nullable = false)
    private String currency;
}
