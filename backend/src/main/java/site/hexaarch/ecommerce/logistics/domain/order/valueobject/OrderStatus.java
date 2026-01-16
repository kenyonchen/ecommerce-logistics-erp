package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import lombok.Getter;

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