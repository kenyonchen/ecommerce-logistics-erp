package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

/**
 * 物流状态JPA枚举。
 *
 * @author kenyon
 */
public enum LogisticsStatusJpaEntity {
    PENDING,        // 待处理
    PICKED_UP,      // 已揽收
    IN_TRANSIT,     // 运输中
    OUT_FOR_DELIVERY, // 派送中
    DELIVERED,      // 已送达
    EXCEPTION,      // 异常
    LABEL_GENERATED, CANCELLED       // 已取消
}
