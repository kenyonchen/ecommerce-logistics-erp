package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 物流渠道JPA实体，用于持久化物流渠道实体。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_channels")
@Getter
@Setter
public class LogisticsChannelJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "channel_name", unique = true, nullable = false)
    private String channelName;

    @Column(name = "channel_code", unique = true, nullable = false)
    private String channelCode;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "base_price")
    private Double basePrice;

    @Column(name = "price_per_gram")
    private Double pricePerGram;
}
