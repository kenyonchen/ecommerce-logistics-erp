package site.hexaarch.ecommerce.logistics.application.command;

import lombok.Value;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

/**
 * 更新订单状态命令
 *
 * @author kenyon
 */
@Value
public class UpdateOrderStatusCommand implements Command {
    String orderId;
    OrderStatus newStatus;

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }
}