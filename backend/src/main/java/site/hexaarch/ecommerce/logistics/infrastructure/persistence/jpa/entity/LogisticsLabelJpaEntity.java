package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 物流标签JPA实体，用于持久化物流标签值对象。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_labels")
@Getter
@Setter
public class LogisticsLabelJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "label_data", columnDefinition = "TEXT")
    private String labelData;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "label_format")
    private String labelFormat;
}
