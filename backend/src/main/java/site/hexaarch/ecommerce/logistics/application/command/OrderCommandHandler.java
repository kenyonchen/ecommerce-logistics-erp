package site.hexaarch.ecommerce.logistics.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;

/**
 * 订单命令处理器
 * 处理与订单相关的命令
 *
 * @author kenyon
 */
@Component
@RequiredArgsConstructor
public class OrderCommandHandler {

    private final OrderApplicationService orderApplicationService;

    public Order handle(CreateOrderCommand command) {
        return orderApplicationService.createOrder(
                command.getTenantId(),
                command.getCustomerId(),
                command.getOrderItems(),
                command.getShippingAddress(),
                command.getPaymentInfo()
        );
    }

    public Order handle(UpdateOrderStatusCommand command) {
        return orderApplicationService.updateOrderStatus(
                command.getOrderId(),
                command.getNewStatus()
        );
    }
}