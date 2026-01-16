package site.hexaarch.ecommerce.logistics.domain.order.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 订单完成事件，当订单完成时触发。
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
public class OrderCompletedEvent {
    /**
     * 订单ID
     */
    private final String orderId;
    /**
     * 订单完成时间
     */
    private final LocalDateTime completedAt;

    /**
     * 构造函数。
     *
     * @param orderId     订单ID
     * @param completedAt 订单完成时间
     */
    public OrderCompletedEvent(String orderId, LocalDateTime completedAt) {
        this.orderId = orderId;
        this.completedAt = completedAt;
    }
}