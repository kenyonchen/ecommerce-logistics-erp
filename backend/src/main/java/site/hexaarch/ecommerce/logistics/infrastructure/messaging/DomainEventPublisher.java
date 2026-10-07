package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * 发布聚合根中的所有领域事件
     *
     * @param aggregate 聚合根
     */
    public void publishEventsFrom(Object aggregate) {
        try {
            // 优先走基类访问器，避免 getDeclaredField 取不到父类字段导致事件永远发不出去
            List<Object> events = resolveDomainEvents(aggregate);

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
        } catch (Exception e) {
            log.error("发布领域事件时出错", e);
        }
    }

    /**
     * 获取聚合根中待发布的领域事件列表：
     * 基类聚合走 {@link BaseAggregateRoot#getDomainEvents()}，其余对象回退到沿继承链反射查找。
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveDomainEvents(Object aggregate) {
        if (aggregate instanceof site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot<?> root) {
            return root.getDomainEvents();
        }
        Class<?> clazz = aggregate.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("domainEvents");
                field.setAccessible(true);
                return (List<Object>) field.get(aggregate);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将事件发布到RocketMQ
     *
     * @param event 领域事件
     */
    private void publishToRocketMQ(Object event) {
        try {
            RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
            if (rocketMQTemplate == null) {
                log.debug("RocketMQ 未启用，事件仅走进程内通道: {}", event.getClass().getSimpleName());
                return;
            }
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
        } else if (eventClassName.contains("customer.event")) {
            return "customer-events";
        } else if (eventClassName.contains("logistics.event")) {
            return "logistics-events";
        } else if (eventClassName.contains("warehouse.event")) {
            return "inventory-events";
        } else if (eventClassName.contains("finance.event")) {
            return "finance-events";
        } else if (eventClassName.contains("purchase.event")) {
            return "purchase-events";
        } else if (eventClassName.contains("product.event")) {
            return "product-events";
        } else if (eventClassName.contains("platform.event")) {
            return "platform-events";
        } else if (eventClassName.contains("shop.event")) {
            return "shop-events";
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