package site.hexaarch.ecommerce.logistics.domain.shop.event;

import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;

/**
 * 店铺授权状态变更事件。
 * <p>
 * 只在状态真的变化时注册，供「某站点令牌过期」这类需要通知运维的场景消费。
 * </p>
 *
 * @author kenyon
 */
public class ShopAuthStatusChangedEvent extends BaseDomainEvent {

    private final String shopId;
    private final String tenantId;
    private final ShopAuthStatus status;

    public ShopAuthStatusChangedEvent(String shopId, String tenantId, ShopAuthStatus status) {
        this.shopId = shopId;
        this.tenantId = tenantId;
        this.status = status;
    }

    public String getShopId() {
        return shopId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public ShopAuthStatus getStatus() {
        return status;
    }
}
