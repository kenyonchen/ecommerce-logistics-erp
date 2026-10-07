package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

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
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;

import java.time.LocalDateTime;

/**
 * 物流字段映射JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_field_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_logistics_field_mappings_ref",
                columnNames = {"tenant_id", "platform", "source_field"}),
        indexes = @Index(name = "idx_logistics_field_mappings_lookup", columnList = "tenant_id,platform"))
@Getter
@Setter
public class LogisticsFieldMappingJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "source_field", nullable = false, length = 128)
    private String sourceField;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", nullable = false, length = 64)
    private LogisticsImportTargetField targetField;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform", length = 32)
    private FieldTransform transform;

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
