package site.hexaarch.ecommerce.logistics.application.command;

import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.util.List;

/**
 * 创建订单命令
 *
 * @author kenyon
 */
public class CreateOrderCommand implements Command {
    private final String tenantId;
    private final String customerId;
    private final List<OrderItem> orderItems;
    private final ShippingAddress shippingAddress;
    private final PaymentInfo paymentInfo;

    public CreateOrderCommand(String tenantId, String customerId, List<OrderItem> orderItems, ShippingAddress shippingAddress, PaymentInfo paymentInfo) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.orderItems = orderItems;
        this.shippingAddress = shippingAddress;
        this.paymentInfo = paymentInfo;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }
}