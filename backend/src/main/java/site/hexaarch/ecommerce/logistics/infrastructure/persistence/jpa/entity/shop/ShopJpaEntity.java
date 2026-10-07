package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 店铺 JPA 实体（表 {@code shops}）。
 * <p>
 * <b>状态类列一律是 {@code varchar}，不用 {@code @Enumerated(EnumType.STRING)}</b>：
 * 后者会让 {@code ddl-auto=update} 建出 MySQL ENUM，而 ENUM 是「只加不减不改类型」的窄化陷阱
 * （ADR-0002 / ADR-0013 §1.3——{@code platform_integrations.platform_name} 就是这么把 5 个平台挡在建表之外的）。
 * 枚举↔字符串的转换收敛在 {@code ShopMapper}。
 * </p>
 * <p>
 * 密文列宽度按 ADR-0013 §2.2 放宽：{@code enc:v1:} + Base64 让密文比原文长约
 * {@code 7 + 4/3 × (原文 + 28)}，旧表的 255 只容纳约 180 字符原文，eBay 的 user access token 可能超 1024。
 * </p>
 *
 * @author kenyon
 */
@Entity
@Table(name = "shops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shops_tenant_code", columnNames = {"tenant_id", "shop_code"}),
                // 禁止同一站点绑两次；MySQL 唯一索引不约束 NULL，所以无站点概念的平台天然不受限
                @UniqueConstraint(name = "uk_shops_marketplace", columnNames = {"tenant_id", "platform", "marketplace_id"})
        },
        indexes = {
                @Index(name = "idx_shops_tenant", columnList = "tenant_id"),
                @Index(name = "idx_shops_integration", columnList = "integration_id")
        })
@Getter
@Setter
public class ShopJpaEntity {

    @Id
    @Column(name = "id", length = 36, columnDefinition = "varchar(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, columnDefinition = "varchar(64)", nullable = false)
    private String tenantId;

    /**
     * 指向对接定义；自授权模式下为空
     */
    @Column(name = "integration_id", length = 36, columnDefinition = "varchar(36)")
    private String integrationId;

    @Column(name = "platform", length = 32, columnDefinition = "varchar(32)", nullable = false)
    private String platform;

    @Column(name = "shop_code", length = 128, columnDefinition = "varchar(128)", nullable = false)
    private String shopCode;

    @Column(name = "shop_name", length = 128, columnDefinition = "varchar(128)", nullable = false)
    private String shopName;

    @Column(name = "marketplace_id", length = 64, columnDefinition = "varchar(64)")
    private String marketplaceId;

    @Column(name = "region", length = 32, columnDefinition = "varchar(32)")
    private String region;

    @Column(name = "external_shop_id", length = 128, columnDefinition = "varchar(128)")
    private String externalShopId;

    @Column(name = "store_url", length = 255)
    private String storeUrl;

    /**
     * AES-256-GCM 密文（{@code enc:v1:} 前缀），加解密在仓储边界完成
     */
    @Column(name = "api_key", length = 512)
    private String apiKey;

    @Column(name = "api_secret", length = 512)
    private String apiSecret;

    @Column(name = "access_token", length = 2048)
    private String accessToken;

    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    /**
     * 令牌到期时间，非敏感，出参可见
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "api_version", length = 32, columnDefinition = "varchar(32)")
    private String apiVersion;

    /**
     * 授权状态：PENDING/ACTIVE/ERROR/EXPIRED
     */
    @Column(name = "auth_status", length = 24, columnDefinition = "varchar(24)", nullable = false)
    private String authStatus;

    /**
     * 发货回写策略：AUTO/MANUAL/OFF
     */
    @Column(name = "push_back_policy", length = 16, columnDefinition = "varchar(16)", nullable = false)
    private String pushBackPolicy;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_orders_synced_at")
    private LocalDateTime lastOrdersSyncedAt;

    @Column(name = "last_products_synced_at")
    private LocalDateTime lastProductsSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
