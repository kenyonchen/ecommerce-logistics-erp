package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import lombok.Getter;

import java.util.List;

/**
 * 订单状态值对象，表示订单的当前状态。
 *
 * @author kenyon
 */
@Getter
public enum OrderStatus {
    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    CON_FIRMED("confirmed", "已确认"),
    SHIPPED("shipped", "已发货"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    // 手动添加构造器，解决Lombok在枚举中无法正常生成构造器的问题
    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态的中文描述（同时作为前端展示文案）
     */
    public String getLabel() {
        return description;
    }

    /**
     * 允许的下一状态集合。终态（已完成/已取消）不允许再流转；
     * 已发货订单不可取消（与 {@code OrderProcessingService.cancelOrder} 同一口径，spec/order.md BR 状态机）。
     */
    public List<OrderStatus> allowedNextStatuses() {
        return switch (this) {
            case PENDING -> List.of(PROCESSING, CON_FIRMED, CANCELLED);
            case PROCESSING -> List.of(CON_FIRMED, SHIPPED, CANCELLED);
            case CON_FIRMED -> List.of(PROCESSING, SHIPPED, CANCELLED);
            case SHIPPED -> List.of(COMPLETED);
            case COMPLETED, CANCELLED -> List.of();
        };
    }

    /**
     * 判断当前状态是否可以流转到目标状态。
     * <p>
     * 规则：相同状态视为无变化（允许，幂等）；终态不可再流转。
     * </p>
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return allowedNextStatuses().contains(target);
    }

    /**
     * 根据状态编码解析枚举，兼容 "confirmed" 与 "CON_FIRMED" 两种写法。
     */
    public static java.util.Optional<OrderStatus> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = code.trim();
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(normalized) || status.code.equalsIgnoreCase(normalized)) {
                return java.util.Optional.of(status);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * 检查订单是否为待处理状态。
     *
     * @return 如果订单状态为待处理则返回true，否则返回false
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 检查订单是否为处理中状态。
     *
     * @return 如果订单状态为处理中则返回true，否则返回false
     */
    public boolean isProcessing() {
        return this == PROCESSING;
    }

    /**
     * 检查订单是否为已确认状态。
     *
     * @return 如果订单状态为已确认则返回true，否则返回false
     */
    public boolean isConfirmed() {
        return this == CON_FIRMED;
    }

    /**
     * 检查订单是否为已发货状态。
     *
     * @return 如果订单状态为已发货则返回true，否则返回false
     */
    public boolean isShipped() {
        return this == SHIPPED;
    }

    /**
     * 检查订单是否为已完成状态。
     *
     * @return 如果订单状态为已完成则返回true，否则返回false
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 检查订单是否为已取消状态。
     *
     * @return 如果订单状态为已取消则返回true，否则返回false
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}