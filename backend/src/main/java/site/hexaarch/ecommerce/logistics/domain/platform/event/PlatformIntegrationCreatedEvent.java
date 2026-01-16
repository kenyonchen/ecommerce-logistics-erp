package site.hexaarch.ecommerce.logistics.domain.platform.event;

import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;

import java.util.UUID;

/**
 * 平台集成创建事件
 *
 * @author kenyon
 */
public class PlatformIntegrationCreatedEvent extends BaseDomainEvent {

    private final UUID platformId;
    private final String tenantId;
    private final PlatformName platformName;

    public PlatformIntegrationCreatedEvent(UUID platformId, String tenantId, PlatformName platformName) {
        this.platformId = platformId;
        this.tenantId = tenantId;
        this.platformName = platformName;
    }

    public UUID getPlatformId() {
        return platformId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public PlatformName getPlatformName() {
        return platformName;
    }
}
