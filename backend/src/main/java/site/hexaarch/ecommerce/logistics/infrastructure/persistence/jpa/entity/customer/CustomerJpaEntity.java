package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.time.LocalDateTime;

/**
 * 客户JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customers_email", columnNames = {"email"}),
                @UniqueConstraint(name = "uk_customers_external_buyer",
                        columnNames = {"tenant_id", "source_platform", "external_buyer_id"})
        },
        indexes = {
                @Index(name = "idx_customers_tenant", columnList = "tenant_id"),
                @Index(name = "idx_customers_status", columnList = "status")
        })
@Getter
@Setter
public class CustomerJpaEntity {

    /**
     * 客户业务ID（CUST- 前缀），与领域聚合 customerId 一致
     */
    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 邮箱：跨境买家的主要身份线索，允许缺失（平台侧不一定返回）
     */
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "address", length = 512)
    private String address;

    /**
     * 公司名（跨境B2B买家），手动创建可空
     */
    @Column(name = "company", length = 256)
    private String company;

    /**
     * 来源平台（第三方电商平台名称），手动创建时为空
     */
    @Column(name = "source_platform", length = 32)
    private String sourcePlatform;

    /**
     * 平台侧买家ID，与来源平台一起用于重复导入的幂等匹配
     */
    @Column(name = "external_buyer_id", length = 64)
    private String externalBuyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CustomerStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CustomerJpaEntity() {
        this.status = CustomerStatus.ACTIVE;
    }
}
