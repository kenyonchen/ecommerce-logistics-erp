package site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent;

/**
 * 监听订单状态变更事件 - 从订单上下文到仓储上下文
 *
 * @author kenyon
 */
@Component
// 单体内部编排只由进程内 EventHandler 执行一次；MQ 消费端默认不装配，
// 否则同一事件会被"进程内 + MQ"各跑一遍（重复物流单/重复采购单，ISS-054）。
// 需要跨进程消费内部事件时显式设 rocketmq.internal-consumers.enabled=true。
@ConditionalOnProperty(prefix = "rocketmq", name = "internal-consumers.enabled", havingValue = "true")
@RocketMQMessageListener(topic = "order-events", consumerGroup = "warehouse-group")
public class OrderStatusChangedEventConsumer implements RocketMQListener<OrderStatusChangedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedEventConsumer.class);

    @Override
    public void onMessage(OrderStatusChangedEvent event) {
        log.info("从RocketMQ接收到订单状态变更事件: {} 从 {} 到 {}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus());

        // 订单确认后应预留库存；仓储侧预留接口尚未实现，此处仅记录意图
        if (event.getNewStatus().isConfirmed()) {
            log.info("订单 {} 已确认，预留库存", event.getOrderId());
        }
    }
}
