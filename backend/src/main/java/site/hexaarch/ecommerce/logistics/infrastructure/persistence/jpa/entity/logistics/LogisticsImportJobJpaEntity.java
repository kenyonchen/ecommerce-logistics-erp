package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流单导入任务JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_import_jobs",
        indexes = @Index(name = "idx_logistics_import_jobs_tenant", columnList = "tenant_id"))
@Getter
@Setter
public class LogisticsImportJobJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "integration_id", length = 64)
    private String integrationId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ImportJobStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_logistics_order_ids", length = 4000)
    private String createdLogisticsOrderIds;

    @Column(name = "updated_logistics_order_ids", length = 4000)
    private String updatedLogisticsOrderIds;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LogisticsImportJobErrorJpaEntity> errors;
}
