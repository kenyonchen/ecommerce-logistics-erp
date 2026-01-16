package site.hexaarch.ecommerce.logistics.domain.common;

import java.time.LocalDateTime;

/**
 * 领域事件基类
 *
 * @author kenyon
 */
public abstract class BaseDomainEvent {
    private final String eventId;
    private final LocalDateTime occurredOn;

    public BaseDomainEvent() {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.occurredOn = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }
}