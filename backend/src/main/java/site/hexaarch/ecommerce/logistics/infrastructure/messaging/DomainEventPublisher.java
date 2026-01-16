package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 领域事件发布服务
 * 负责在事务提交后发布聚合根中收集的领域事件
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 发布聚合根中的所有领域事件
     *
     * @param aggregate 聚合根
     */
    public void publishEventsFrom(Object aggregate) {
        try {
            // 使用反射获取domainEvents字段
            java.lang.reflect.Field domainEventsField = aggregate.getClass().getDeclaredField("domainEvents");
            domainEventsField.setAccessible(true);
            List<Object> events = (List<Object>) domainEventsField.get(aggregate);

            if (events != null && !events.isEmpty()) {
                events.forEach(event -> {
                    log.info("发布领域事件: {}", event.getClass().getSimpleName());
                    applicationEventPublisher.publishEvent(event);
                });
                // 清空已发布的事件
                events.clear();
            }
        } catch (NoSuchFieldException e) {
            // 如果对象没有domainEvents字段，则忽略
            log.debug("对象没有domainEvents字段: {}", aggregate.getClass().getName());
        } catch (Exception e) {
            log.error("发布领域事件时出错", e);
        }
    }

    /**
     * 监听事务回滚事件，清理未发布的事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleRollback(Object event) {
        log.info("事务回滚，清理事件: {}", event.getClass().getSimpleName());
    }
}