package site.hexaarch.ecommerce.logistics.domain.purchase.valueobject;

import lombok.Getter;

/**
 * 采购单状态值对象，表示采购单的当前状态。
 *
 * @author kenyon
 */
@Getter
public enum PurchaseStatus {
    PENDING("pending", "待处理"),
    APPROVED("approved", "已批准"),
    REJECTED("rejected", "已拒绝"),
    IN_TRANSIT("in_transit", "运输中"),
    DELIVERED("delivered", "已送达"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    PurchaseStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态编码。
     *
     * @return 状态编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取状态描述。
     *
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 检查采购单是否为待处理状态。
     *
     * @return 如果采购单状态为待处理则返回true，否则返回false
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 检查采购单是否为已批准状态。
     *
     * @return 如果采购单状态为已批准则返回true，否则返回false
     */
    public boolean isApproved() {
        return this == APPROVED;
    }

    /**
     * 检查采购单是否为已拒绝状态。
     *
     * @return 如果采购单状态为已拒绝则返回true，否则返回false
     */
    public boolean isRejected() {
        return this == REJECTED;
    }

    /**
     * 检查采购单是否为运输中状态。
     *
     * @return 如果采购单状态为运输中则返回true，否则返回false
     */
    public boolean isInTransit() {
        return this == IN_TRANSIT;
    }

    /**
     * 检查采购单是否为已送达状态。
     *
     * @return 如果采购单状态为已送达则返回true，否则返回false
     */
    public boolean isDelivered() {
        return this == DELIVERED;
    }

    /**
     * 检查采购单是否为已取消状态。
     *
     * @return 如果采购单状态为已取消则返回true，否则返回false
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
