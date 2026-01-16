package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

/**
 * 订单状态JPA枚举。
 *
 * @author kenyon
 */
public enum OrderStatusJpaEntity {
    PENDING,      // 待处理
    PROCESSING,   // 处理中
    CONFIRMED,    // 已确认
    SHIPPED,      // 已发货
    COMPLETED,    // 已完成
    CANCELLED     // 已取消
}
