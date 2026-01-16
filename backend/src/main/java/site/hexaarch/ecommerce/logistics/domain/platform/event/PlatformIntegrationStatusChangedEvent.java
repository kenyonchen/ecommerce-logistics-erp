package site.hexaarch.ecommerce.logistics.domain.platform.event;

import site.hexaarch.ecommerce.logistics.domain.common.BaseDomainEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;

import java.util.UUID;

/**
 * 平台集成状态变更事件
 *
 * @author kenyon
 */
public class PlatformIntegrationStatusChangedEvent extends BaseDomainEvent {

    private final UUID platformId;
    private final PlatformStatus newStatus;

    public PlatformIntegrationStatusChangedEvent(UUID platformId, PlatformStatus newStatus) {
        this.platformId = platformId;
        this.newStatus = newStatus;
    }

    public UUID getPlatformId() {
        return platformId;
    }

    public PlatformStatus getNewStatus() {
        return newStatus;
    }
}
