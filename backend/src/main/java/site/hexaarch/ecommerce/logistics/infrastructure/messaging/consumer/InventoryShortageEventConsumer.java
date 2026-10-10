package site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.purchase.ReplenishmentApplicationService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

/**
 * 监听库存不足事件 - 从仓储上下文到采购上下文
 *
 * @author kenyon
 */
@Component
// 单体内部编排只由进程内 EventHandler 执行一次；MQ 消费端默认不装配，
// 否则同一事件会被"进程内 + MQ"各跑一遍（重复物流单/重复采购单，ISS-054）。
// 需要跨进程消费内部事件时显式设 rocketmq.internal-consumers.enabled=true。
@ConditionalOnProperty(prefix = "rocketmq", name = "internal-consumers.enabled", havingValue = "true")
@RocketMQMessageListener(topic = "inventory-events", consumerGroup = "purchase-group")
public class InventoryShortageEventConsumer implements RocketMQListener<InventoryShortageEvent> {

    private static final Logger log = LoggerFactory.getLogger(InventoryShortageEventConsumer.class);

    private final ReplenishmentApplicationService replenishmentApplicationService;

    public InventoryShortageEventConsumer(ReplenishmentApplicationService replenishmentApplicationService) {
        this.replenishmentApplicationService = replenishmentApplicationService;
    }

    @Override
    public void onMessage(InventoryShortageEvent event) {
        log.info("从RocketMQ接收到库存不足事件: 产品 {} 在仓库 {} 数量 {}",
                event.getProductId(), event.getWarehouseId(), event.getCurrentQuantity());

        // 与进程内 EventHandler 走同一个补货方法：两条通道各写一套语义就会建出没有明细行的空单（ISS-143）
        try {
            replenishmentApplicationService.replenishForShortage(event)
                    .ifPresent(order -> log.info("为产品 {} 自动创建补货采购单 {} 成功",
                            event.getProductId(), order.getPurchaseOrderId()));
        } catch (Exception e) {
            log.error("为产品 {} 自动创建采购单失败", event.getProductId(), e);
        }
    }
}
