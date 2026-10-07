package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform;

/**
 * 平台对接协议JPA实体枚举（与领域枚举 {@code PlatformAuthType} 同名同序）
 *
 * @author kenyon
 */
public enum PlatformAuthTypeJpaEntity {
    CUSTOM_SHOP,
    SHOPIFY,
    MAGENTO,
    AMAZON_SP_API,
    EBAY_SELL,
    TEMU,
    TIKTOK_SHOP,
    ALIEXPRESS,
    SHOPEE,
    MERCADO_LIBRE,
    OZON
}
