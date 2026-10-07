package site.hexaarch.ecommerce.logistics.domain.order.repository;

import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

import java.time.LocalDateTime;

/**
 * 订单列表查询条件。
 *
 * @param tenantId        租户ID（为空表示不按租户过滤）
 * @param orderNumber     订单号（精确/模糊由实现决定，此处为模糊）
 * @param customerId      客户ID
 * @param customerName    客户名称（模糊）
 * @param status          订单状态
 * @param platform        来源平台
 * @param platformOrderId 第三方平台订单号
 * @param keyword         关键字，同时匹配订单号/客户名称/客户ID/平台订单号
 * @param createdFrom     创建时间起（含）
 * @param createdTo       创建时间止（含）
 * @author kenyon
 */
public record OrderQuery(String tenantId,
                         String orderNumber,
                         String customerId,
                         String customerName,
                         OrderStatus status,
                         String platform,
                         String platformOrderId,
                         String keyword,
                         LocalDateTime createdFrom,
                         LocalDateTime createdTo) {

    public static OrderQuery of(String tenantId) {
        return new OrderQuery(tenantId, null, null, null, null, null, null, null, null, null);
    }
}
