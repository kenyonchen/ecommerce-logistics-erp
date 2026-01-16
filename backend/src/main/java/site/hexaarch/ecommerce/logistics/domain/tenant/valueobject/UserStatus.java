package site.hexaarch.ecommerce.logistics.domain.tenant.valueobject;

/**
 * 用户状态枚举
 *
 * @author kenyon
 */
public enum UserStatus {
    ACTIVE,    // 活跃
    INACTIVE,  // 非活跃
    LOCKED,    // 锁定
    PENDING    // 待激活
}