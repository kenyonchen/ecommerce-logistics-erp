package site.hexaarch.ecommerce.logistics.domain.shop.event;

import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;

/**
 * 店铺创建事件。
 *
 * @author kenyon
 */
public class ShopCreatedEvent extends BaseDomainEvent {

    private final String shopId;
    private final String tenantId;
    private final String platform;
    private final String shopCode;

    public ShopCreatedEvent(String shopId, String tenantId, String platform, String shopCode) {
        this.shopId = shopId;
        this.tenantId = tenantId;
        this.platform = platform;
        this.shopCode = shopCode;
    }

    public String getShopId() {
        return shopId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getShopCode() {
        return shopCode;
    }
}
