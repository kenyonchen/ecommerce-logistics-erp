package site.hexaarch.ecommerce.logistics.application.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;

/**
 * 订单查询处理器
 * 处理与订单相关的查询
 *
 * @author kenyon
 */
@Component
@RequiredArgsConstructor
public class OrderQueryHandler {

    private final OrderApplicationService orderApplicationService;

    public Order handle(FindOrderByIdQuery query) {
        return orderApplicationService.findOrderById(query.getOrderId());
    }
}