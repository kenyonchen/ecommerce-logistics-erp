package site.hexaarch.ecommerce.logistics.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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
 * 事件处理器，处理跨限界上下文的领域事件
 * 实现文档中提到的上下文集成模式
 *
 * @author kenyon
 */
@Component
public class EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventHandler.class);

    private final LogisticsApplicationService logisticsApplicationService;
    private final WarehouseApplicationService warehouseApplicationService;
    private final PurchaseApplicationService purchaseApplicationService;
    private final FinanceApplicationService financeApplicationService;

    public EventHandler(LogisticsApplicationService logisticsApplicationService,
                        WarehouseApplicationService warehouseApplicationService,
                        PurchaseApplicationService purchaseApplicationService,
                        FinanceApplicationService financeApplicationService) {
        this.logisticsApplicationService = logisticsApplicationService;
        this.warehouseApplicationService = warehouseApplicationService;
        this.purchaseApplicationService = purchaseApplicationService;
        this.financeApplicationService = financeApplicationService;
    }

    /**
     * 处理订单创建事件 - 订单上下文 → 物流上下文（事件发布/订阅模式）
     */
    @EventListener
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("接收到订单创建事件: {}", event.getOrderId());

        // 自动为新订单创建物流单
        try {
            logisticsApplicationService.createLogisticsOrder(event.getTenantId(), event.getOrderId(), "DEFAULT_CHANNEL");
            log.info("为订单 {} 自动创建物流单成功", event.getOrderId());
        } catch (Exception e) {
            log.error("为订单 {} 自动创建物流单失败", event.getOrderId(), e);
        }
    }

    /**
     * 处理订单状态变更事件 - 订单上下文 → 仓储上下文（事件发布/订阅模式）
     */
    @EventListener
    public void handleOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        log.info("接收到订单状态变更事件: {} 从 {} 到 {}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus());

        // 如果订单状态变为CONFIRMED，预留库存
        if (event.getNewStatus().isConfirmed()) {
            // 这里可以调用仓储服务预留库存
            log.info("订单 {} 已确认，预留库存", event.getOrderId());
        }
    }

    /**
     * 处理物流完成事件 - 物流上下文 → 财务上下文（事件发布/订阅模式）
     */
    @EventListener
    public void handleLogisticsCompletedEvent(LogisticsCompletedEvent event) {
        log.info("接收到物流完成事件: {}", event.getLogisticsOrderId());

        // 物流完成后，生成财务交易记录
        try {
            // 这里可以根据物流费用创建财务交易
            log.info("物流 {} 完成，生成财务结算记录", event.getLogisticsOrderId());
        } catch (Exception e) {
            log.error("处理物流完成事件失败", e);
        }
    }

    /**
     * 处理库存不足事件 - 仓储上下文 → 采购上下文（事件发布/订阅模式）
     */
    @EventListener
    public void handleInventoryShortageEvent(InventoryShortageEvent event) {
        log.info("接收到库存不足事件: 产品 {} 在仓库 {} 数量 {}",
                event.getProductId(), event.getWarehouseId(), event.getCurrentQuantity());

        // 库存不足时自动生成采购单
        try {
            // 使用正确的参数调用createPurchaseOrder方法，使用默认的tenantId
            purchaseApplicationService.createPurchaseOrder("DEFAULT_TENANT", "DEFAULT_SUPPLIER", "Default Supplier", event.getWarehouseId());
            log.info("为产品 {} 自动创建采购单成功", event.getProductId());
        } catch (Exception e) {
            log.error("为产品 {} 自动创建采购单失败", event.getProductId(), e);
        }
    }
}