package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
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
    private final RocketMQTemplate rocketMQTemplate;

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
                    // 发布到本地Spring事件
                    applicationEventPublisher.publishEvent(event);
                    // 发布到RocketMQ
                    publishToRocketMQ(event);
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
     * 将事件发布到RocketMQ
     *
     * @param event 领域事件
     */
    private void publishToRocketMQ(Object event) {
        try {
            String topic = getTopicForEvent(event);
            if (topic != null) {
                rocketMQTemplate.convertAndSend(topic, event);
                log.info("事件已发布到RocketMQ主题: {}", topic);
            } else {
                log.warn("未知事件类型，无法确定RocketMQ主题: {}", event.getClass().getName());
            }
        } catch (Exception e) {
            log.error("发布事件到RocketMQ失败", e);
        }
    }

    /**
     * 根据事件类型获取对应的RocketMQ主题
     *
     * @param event 领域事件
     * @return RocketMQ主题名称
     */
    private String getTopicForEvent(Object event) {
        String eventClassName = event.getClass().getName();
        if (eventClassName.contains("order.event")) {
            return "order-events";
        } else if (eventClassName.contains("logistics.event")) {
            return "logistics-events";
        } else if (eventClassName.contains("warehouse.event")) {
            return "inventory-events";
        } else if (eventClassName.contains("finance.event")) {
            return "finance-events";
        } else if (eventClassName.contains("purchase.event")) {
            return "purchase-events";
        }
        return null;
    }

    /**
     * 监听事务回滚事件，清理未发布的事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleRollback(Object event) {
        log.info("事务回滚，清理事件: {}", event.getClass().getSimpleName());
    }
}