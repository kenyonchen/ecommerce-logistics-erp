package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 平台集成JPA实体
 *
 * @author kenyon
 */
@Entity
@Table(name = "platform_integrations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformIntegrationJpaEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "platform_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlatformNameJpaEntity platformName;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "api_secret", nullable = false)
    private String apiSecret;

    @Column(name = "store_url")
    private String storeUrl;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlatformStatusJpaEntity status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
