package site.hexaarch.ecommerce.logistics.domain.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合根基类
 *
 * @author kenyon
 */
public abstract class BaseAggregateRoot<T> {
    protected List<Object> domainEvents;

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }

    /**
     * 注册领域事件
     */
    protected void registerDomainEvent(Object event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }
}