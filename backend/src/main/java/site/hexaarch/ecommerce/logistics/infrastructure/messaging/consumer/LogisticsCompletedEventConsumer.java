package site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;

/**
 * 监听物流完成事件 - 从物流上下文到财务上下文
 *
 * @author kenyon
 */
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "logistics-events", consumerGroup = "finance-group")
public class LogisticsCompletedEventConsumer implements RocketMQListener<LogisticsCompletedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LogisticsCompletedEventConsumer.class);

    @Override
    public void onMessage(LogisticsCompletedEvent event) {
        log.info("从RocketMQ接收到物流完成事件: {}", event.getLogisticsOrderId());

        // 物流费用到财务结算记录的记账规则未定义，此处仅记录事件到达
        log.info("物流 {} 完成，生成财务结算记录", event.getLogisticsOrderId());
    }
}
