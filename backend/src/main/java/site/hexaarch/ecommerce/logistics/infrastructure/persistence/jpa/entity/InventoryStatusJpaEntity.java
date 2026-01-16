package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

/**
 * 库存状态JPA枚举。
 *
 * @author kenyon
 */
public enum InventoryStatusJpaEntity {
    NORMAL,     // 正常
    SHORTAGE,   // 不足
    EXCESS,     // 过剩
    OUT_OF_STOCK     // 缺货
}
