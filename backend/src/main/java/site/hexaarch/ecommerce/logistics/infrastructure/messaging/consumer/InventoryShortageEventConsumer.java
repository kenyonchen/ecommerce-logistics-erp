package site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

/**
 * 监听库存不足事件 - 从仓储上下文到采购上下文
 *
 * @author kenyon
 */
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "inventory-events", consumerGroup = "purchase-group")
public class InventoryShortageEventConsumer implements RocketMQListener<InventoryShortageEvent> {

    private static final Logger log = LoggerFactory.getLogger(InventoryShortageEventConsumer.class);

    private final PurchaseApplicationService purchaseApplicationService;

    public InventoryShortageEventConsumer(PurchaseApplicationService purchaseApplicationService) {
        this.purchaseApplicationService = purchaseApplicationService;
    }

    @Override
    public void onMessage(InventoryShortageEvent event) {
        log.info("从RocketMQ接收到库存不足事件: 产品 {} 在仓库 {} 数量 {}",
                event.getProductId(), event.getWarehouseId(), event.getCurrentQuantity());

        // InventoryShortageEvent 载荷里没有 tenantId，只能落默认租户；与进程内 EventHandler 同源，双开会重复建采购单
        try {
            purchaseApplicationService.createPurchaseOrder("default-tenant",
                    "DEFAULT_SUPPLIER", "Default Supplier", event.getWarehouseId());
            log.info("为产品 {} 自动创建采购单成功", event.getProductId());
        } catch (Exception e) {
            log.error("为产品 {} 自动创建采购单失败", event.getProductId(), e);
        }
    }
}
