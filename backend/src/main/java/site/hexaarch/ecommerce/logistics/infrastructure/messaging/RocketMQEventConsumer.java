package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.purchase.PurchaseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsCompletedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.order.event.OrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

/**
 * RocketMQ事件消费者
 * 处理跨限界上下文的异步事件
 *
 * @author kenyon
 */
@Component
public class RocketMQEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(RocketMQEventConsumer.class);

    private final LogisticsApplicationService logisticsApplicationService;
    private final WarehouseApplicationService warehouseApplicationService;
    private final PurchaseApplicationService purchaseApplicationService;
    private final FinanceApplicationService financeApplicationService;

    public RocketMQEventConsumer(LogisticsApplicationService logisticsApplicationService,
                                WarehouseApplicationService warehouseApplicationService,
                                PurchaseApplicationService purchaseApplicationService,
                                FinanceApplicationService financeApplicationService) {
        this.logisticsApplicationService = logisticsApplicationService;
        this.warehouseApplicationService = warehouseApplicationService;
        this.purchaseApplicationService = purchaseApplicationService;
        this.financeApplicationService = financeApplicationService;
    }

    /**
     * 监听订单创建事件 - 从订单上下文到物流上下文
     */
    @Component
    @RocketMQMessageListener(topic = "order-events", consumerGroup = "logistics-group")
    public class OrderCreatedEventConsumer implements RocketMQListener<OrderCreatedEvent> {
        @Override
        public void onMessage(OrderCreatedEvent event) {
            log.info("从RocketMQ接收到订单创建事件: {}", event.getOrderId());

            // 自动为新订单创建物流单
            try {
                logisticsApplicationService.createLogisticsOrder("default-tenant", event.getOrderId(), "DEFAULT_CHANNEL");
                log.info("为订单 {} 自动创建物流单成功", event.getOrderId());
            } catch (Exception e) {
                log.error("为订单 {} 自动创建物流单失败", event.getOrderId(), e);
            }
        }
    }

    /**
     * 监听订单状态变更事件 - 从订单上下文到仓储上下文
     */
    @Component
    @RocketMQMessageListener(topic = "order-events", consumerGroup = "warehouse-group")
    public class OrderStatusChangedEventConsumer implements RocketMQListener<OrderStatusChangedEvent> {
        @Override
        public void onMessage(OrderStatusChangedEvent event) {
            log.info("从RocketMQ接收到订单状态变更事件: {} 从 {} 到 {}",
                    event.getOrderId(), event.getOldStatus(), event.getNewStatus());

            // 如果订单状态变为CONFIRMED，预留库存
            if (event.getNewStatus().isConfirmed()) {
                // 这里可以调用仓储服务预留库存
                log.info("订单 {} 已确认，预留库存", event.getOrderId());
            }
        }
    }

    /**
     * 监听物流完成事件 - 从物流上下文到财务上下文
     */
    @Component
    @RocketMQMessageListener(topic = "logistics-events", consumerGroup = "finance-group")
    public class LogisticsCompletedEventConsumer implements RocketMQListener<LogisticsCompletedEvent> {
        @Override
        public void onMessage(LogisticsCompletedEvent event) {
            log.info("从RocketMQ接收到物流完成事件: {}", event.getLogisticsOrderId());

            // 物流完成后，生成财务交易记录
            try {
                // 这里可以根据物流费用创建财务交易
                log.info("物流 {} 完成，生成财务结算记录", event.getLogisticsOrderId());
            } catch (Exception e) {
                log.error("处理物流完成事件失败", e);
            }
        }
    }

    /**
     * 监听库存不足事件 - 从仓储上下文到采购上下文
     */
    @Component
    @RocketMQMessageListener(topic = "inventory-events", consumerGroup = "purchase-group")
    public class InventoryShortageEventConsumer implements RocketMQListener<InventoryShortageEvent> {
        @Override
        public void onMessage(InventoryShortageEvent event) {
            log.info("从RocketMQ接收到库存不足事件: 产品 {} 在仓库 {} 数量 {}",
                    event.getProductId(), event.getWarehouseId(), event.getCurrentQuantity());

            // 库存不足时自动生成采购单
            try {
                // 使用正确的参数调用createPurchaseOrder方法
                purchaseApplicationService.createPurchaseOrder("default-tenant", "DEFAULT_SUPPLIER", "Default Supplier", event.getWarehouseId());
                log.info("为产品 {} 自动创建采购单成功", event.getProductId());
            } catch (Exception e) {
                log.error("为产品 {} 自动创建采购单失败", event.getProductId(), e);
            }
        }
    }
}