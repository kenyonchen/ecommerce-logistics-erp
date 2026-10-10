package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台系统配置JPA实体，对应 {@code system_configs} 表。
 * <p>
 * 主键用配置键本身（{@code config_key}）而非生成的 UUID：配置项是幂等 upsert 的字典数据，
 * 用键做主键天然避免同一配置出现多行。
 * </p>
 */
@Entity
@Table(name = "system_configs")
@Getter
@Setter
public class SystemSettingJpaEntity {

    @Id
    @Column(name = "config_key", updatable = false, nullable = false, length = 64)
    private String configKey;

    @Column(name = "config_value", length = 512)
    private String configValue;

    @Column(name = "config_group", nullable = false, length = 32)
    private String configGroup;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public SystemSettingJpaEntity() {
        this.updatedAt = LocalDateTime.now();
    }
}
