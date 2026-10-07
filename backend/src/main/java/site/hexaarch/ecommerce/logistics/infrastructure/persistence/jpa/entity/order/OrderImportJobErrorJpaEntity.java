package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单导入任务错误明细JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "order_import_job_errors")
@Getter
@Setter
public class OrderImportJobErrorJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private OrderImportJobJpaEntity job;

    // ROW_NUMBER 是 MySQL 8 保留字，未加引号的 DDL 会执行失败，故物理列改名
    @Column(name = "line_number")
    private Integer rowNumber;

    @Column(name = "external_order_id", length = 128)
    private String externalOrderId;

    @Column(name = "order_number", length = 128)
    private String orderNumber;

    @Column(name = "field_name", length = 64)
    private String field;

    @Column(name = "message", length = 1000)
    private String message;
}
