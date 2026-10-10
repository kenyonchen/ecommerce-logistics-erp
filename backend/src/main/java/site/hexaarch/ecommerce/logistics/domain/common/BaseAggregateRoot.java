package site.hexaarch.ecommerce.logistics.domain.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合根基类
 *
 * @author kenyon
 */
public abstract class BaseAggregateRoot<T> {
    protected List<Object> domainEvents;

    /**
     * 事件缓冲是基础设施细节，不是 REST 契约的一部分；聚合根被直接序列化时会外泄（ISS-063）。
     */
    @JsonIgnore
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

    /**
     * 将 source 聚合上已注册、尚未发布的领域事件搬运到 target 聚合。
     * <p>
     * 仓储层 {@code save()} 会用 JPA 实体重建聚合副本（domainEvents 被置为空列表），
     * 应用服务随后对副本发布事件将拿到空列表（ISS-062）—— 保存后调用本方法把事件补回副本。
     * Order / Warehouse / LogisticsOrder 等聚合未继承本类，因此统一走反射兜底。
     * </p>
     */
    public static void carryOverEvents(Object source, Object target) {
        if (source == null || target == null || source == target) {
            return;
        }
        List<Object> events = extractEvents(source);
        if (events == null || events.isEmpty()) {
            return;
        }
        List<Object> targetEvents = extractEvents(target);
        if (targetEvents == null) {
            return;
        }
        targetEvents.addAll(events);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> extractEvents(Object aggregate) {
        if (aggregate instanceof BaseAggregateRoot<?> base) {
            return base.getDomainEvents();
        }
        Class<?> type = aggregate.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("domainEvents");
                field.setAccessible(true);
                return (List<Object>) field.get(aggregate);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }
}