package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 物流渠道JPA实体，用于持久化物流渠道实体。
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_channels",
        indexes = @Index(name = "idx_logistics_channels_active", columnList = "active"))
@Getter
@Setter
public class LogisticsChannelJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "channel_code", unique = true, nullable = false, length = 64)
    private String channelCode;

    @Column(name = "channel_name", nullable = false, length = 128)
    private String channelName;

    @Column(name = "description", length = 512)
    private String description;

    /**
     * 支持的国家/地区，逗号分隔；为空表示不限
     */
    @Column(name = "countries", length = 512)
    private String countries;

    /**
     * 首重基础费用
     */
    @Column(name = "base_price")
    private Double basePrice;

    /**
     * 每克续重费用
     */
    @Column(name = "price_per_gram")
    private Double pricePerGram;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "active", nullable = false)
    private boolean active;
}
