package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 付款信息JPA实体，用于持久化付款信息值对象。
 *
 * @author kenyon
 */
@Entity
@Table(name = "payment_infos")
@Getter
@Setter
public class PaymentInfoJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "payment_date")
    private Long paymentDate;
}
