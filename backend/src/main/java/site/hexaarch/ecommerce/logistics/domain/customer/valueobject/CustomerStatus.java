package site.hexaarch.ecommerce.logistics.domain.customer.valueobject;

import lombok.Getter;

/**
 * 客户状态值对象，表示客户的当前状态。
 *
 * @author kenyon
 */
@Getter
public enum CustomerStatus {
    ACTIVE("active", "活跃"),
    INACTIVE("inactive", "非活跃"),
    SUSPENDED("suspended", "已暂停"),
    DELETED("deleted", "已删除");

    private final String code;
    private final String description;

    CustomerStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 检查客户是否为活跃状态。
     *
     * @return 如果客户状态为活跃则返回true，否则返回false
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 检查客户是否为非活跃状态。
     *
     * @return 如果客户状态为非活跃则返回true，否则返回false
     */
    public boolean isInactive() {
        return this == INACTIVE;
    }

    /**
     * 检查客户是否为已暂停状态。
     *
     * @return 如果客户状态为已暂停则返回true，否则返回false
     */
    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    /**
     * 检查客户是否为已删除状态。
     *
     * @return 如果客户状态为已删除则返回true，否则返回false
     */
    public boolean isDeleted() {
        return this == DELETED;
    }
}
