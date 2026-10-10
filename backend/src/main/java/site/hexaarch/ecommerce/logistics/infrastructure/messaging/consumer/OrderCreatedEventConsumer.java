package site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderCreatedEvent;

/**
 * 监听订单创建事件 - 从订单上下文到物流上下文
 *
 * @author kenyon
 */
@Component
// 单体内部编排只由进程内 EventHandler 执行一次；MQ 消费端默认不装配，
// 否则同一事件会被"进程内 + MQ"各跑一遍（重复物流单/重复采购单，ISS-054）。
// 需要跨进程消费内部事件时显式设 rocketmq.internal-consumers.enabled=true。
@ConditionalOnProperty(prefix = "rocketmq", name = "internal-consumers.enabled", havingValue = "true")
@RocketMQMessageListener(topic = "order-events", consumerGroup = "logistics-group")
public class OrderCreatedEventConsumer implements RocketMQListener<OrderCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);

    private final LogisticsApplicationService logisticsApplicationService;

    public OrderCreatedEventConsumer(LogisticsApplicationService logisticsApplicationService) {
        this.logisticsApplicationService = logisticsApplicationService;
    }

    @Override
    public void onMessage(OrderCreatedEvent event) {
        log.info("从RocketMQ接收到订单创建事件: {}", event.getOrderId());

        // 与进程内 EventHandler 是双通道同源副作用，同时启用会重复建物流单（收敛方案见 docs/adr/0006-rocketmq-domain-events.md）
        String tenantId = StringUtils.hasText(event.getTenantId())
                ? event.getTenantId()
                : "default-tenant";
        try {
            logisticsApplicationService.createLogisticsOrder(tenantId, event.getOrderId(), "DEFAULT_CHANNEL");
            log.info("为订单 {} 自动创建物流单成功", event.getOrderId());
        } catch (Exception e) {
            log.error("为订单 {} 自动创建物流单失败", event.getOrderId(), e);
        }
    }
}
