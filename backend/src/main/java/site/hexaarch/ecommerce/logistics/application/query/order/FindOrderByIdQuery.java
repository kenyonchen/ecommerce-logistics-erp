package site.hexaarch.ecommerce.logistics.application.query.order;

import site.hexaarch.ecommerce.logistics.application.query.Query;

/**
 * 根据ID查找订单查询
 *
 * @author kenyon
 */
public class FindOrderByIdQuery implements Query {
    private final String orderId;

    public FindOrderByIdQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}