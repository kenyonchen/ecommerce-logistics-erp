package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

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
public class PlatformIntegrationJpaEntity implements Persistable<UUID> {

    /**
     * 主键由聚合 {@code PlatformIntegration.create()} 分配，不是数据库生成，故不加 {@code @GeneratedValue}：
     * 带生成器的 id 一旦非空，Hibernate 就把实体判成游离态，{@code persist()} 直接抛
     * {@code PersistentObjectException: detached entity passed to persist}。
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 新行标记：主键由聚合在入库前预分配，Spring Data 只看 id 是否为空会把新行误判成游离实体走
     * {@code merge()}（SELECT 落空后 UPDATE 0 行 → unsaved-value 异常）。仓储在 save 前写入本标记，
     * {@code SimpleJpaRepository} 据此走 {@code persist()} 做 INSERT。不入库。
     */
    @Transient
    private boolean newRecord;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "platform_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlatformNameJpaEntity platformName;

    /**
     * 对接协议，决定用哪套适配器外呼；历史行为 {@code CUSTOM_SHOP}
     */
    @Column(name = "auth_type", length = 32)
    @Enumerated(EnumType.STRING)
    private PlatformAuthTypeJpaEntity authType;

    /**
     * API Key 密文列：与 {@code access_token} 同宽。密文比明文长（{@code enc:v1:} + Base64(IV+密文+GCM tag)），
     * 默认 255 会让长令牌在写入时报 Data too long（冒烟 7.5）。
     */
    @Column(name = "api_key", nullable = false, length = 1024)
    private String apiKey;

    @Column(name = "api_secret", nullable = false, length = 1024)
    private String apiSecret;

    /**
     * OAuth/访问令牌密文（{@code enc:v1:} 前缀），Shopify 的 {@code shpat_} 令牌落这里
     */
    @Column(name = "access_token", length = 1024)
    private String accessToken;

    /**
     * 刷新令牌密文，供需要令牌轮换的平台使用（Amazon LWA / eBay OAuth）
     */
    @Column(name = "refresh_token", length = 1024)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * 平台接口版本，Shopify 形如 {@code 2026-07}；为空则取全局配置
     */
    @Column(name = "api_version", length = 32)
    private String apiVersion;

    @Column(name = "store_url")
    private String storeUrl;

    /**
     * 平台区域/站点，用于选择 API host；不是凭据，明文存储
     */
    @Column(name = "region", length = 32)
    private String region;

    /**
     * 平台侧店铺/卖家账号标识（{@code shop_id}/{@code user_id}）；不是密钥，明文存储
     */
    @Column(name = "external_shop_id", length = 128)
    private String externalShopId;

    /**
     * 逗号分隔的市场标识（Amazon {@code marketplaceIds} / eBay {@code marketplace_id}）
     */
    @Column(name = "marketplace_ids", length = 512)
    private String marketplaceIds;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlatformStatusJpaEntity status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_products_synced_at")
    private LocalDateTime lastProductsSyncedAt;

    @Column(name = "last_orders_synced_at")
    private LocalDateTime lastOrdersSyncedAt;

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
