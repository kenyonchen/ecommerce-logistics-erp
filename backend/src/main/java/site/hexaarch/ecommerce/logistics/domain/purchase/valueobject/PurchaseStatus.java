package site.hexaarch.ecommerce.logistics.domain.purchase.valueobject;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

import java.util.List;

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

    /**
     * 该状态允许流转到的下一状态白名单。
     * <p>
     * 与 docs/spec/purchase.md §3 的状态机图一致：已发货（IN_TRANSIT）不再允许取消，
     * 终态（DELIVERED / REJECTED / CANCELLED）不可再推进。
     * </p>
     *
     * @return 允许的目标状态，终态返回空列表
     */
    public List<PurchaseStatus> allowedNextStatuses() {
        return switch (this) {
            case PENDING -> List.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> List.of(IN_TRANSIT, CANCELLED);
            case IN_TRANSIT -> List.of(DELIVERED);
            case DELIVERED, REJECTED, CANCELLED -> List.of();
        };
    }

    /**
     * 判断能否流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许则返回true
     */
    public boolean canTransitionTo(PurchaseStatus target) {
        return target != null && allowedNextStatuses().contains(target);
    }

    /**
     * 状态迁移守卫：不在白名单内时抛出携带专用错误码的业务异常。
     * <p>
     * 错误码刻意不用 INVALID_OPERATION，前端 error-codes 映射表会把该码替换成通用文案，
     * 导致「从哪个状态到哪个状态」的具体原因丢失。
     * </p>
     *
     * @param target 目标状态
     */
    public void requireTransitionTo(PurchaseStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(
                    String.format("采购单不允许从「%s」变更为「%s」", description, target == null ? "空状态" : target.description),
                    "INVALID_STATUS_TRANSITION");
        }
    }
}
