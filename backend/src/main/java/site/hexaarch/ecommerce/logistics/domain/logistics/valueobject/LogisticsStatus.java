package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import lombok.Getter;

/**
 * 物流状态值对象，表示物流的当前状态。
 *
 * @author kenyon
 */
@Getter
public enum LogisticsStatus {
    PENDING("pending", "待处理"),
    COLLECTED("collected", "已揽收"),
    LABEL_GENERATED("generated", "已打单"),
    IN_TRANSIT("in_transit", "运输中"),
    DELIVERING("delivering", "派送中"),
    DELIVERED("delivered", "已送达"),
    FAILED("failed", "配送失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    // 手动添加构造器，解决Lombok在枚举中无法正常生成构造器的问题
    LogisticsStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 检查物流是否为待处理状态。
     *
     * @return 如果物流状态为待处理则返回true，否则返回false
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 检查物流是否为已揽收状态。
     *
     * @return 如果物流状态为已揽收则返回true，否则返回false
     */
    public boolean isCollected() {
        return this == COLLECTED;
    }

    /**
     * 检查物流是否为运输中状态。
     *
     * @return 如果物流状态为运输中则返回true，否则返回false
     */
    public boolean isInTransit() {
        return this == IN_TRANSIT;
    }

    /**
     * 检查物流是否为已送达状态。
     *
     * @return 如果物流状态为已送达则返回true，否则返回false
     */
    public boolean isDelivered() {
        return this == DELIVERED;
    }

    /**
     * 检查物流是否为配送失败状态。
     *
     * @return 如果物流状态为配送失败则返回true，否则返回false
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 检查物流是否为已取消状态。
     *
     * @return 如果物流状态为已取消则返回true，否则返回false
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}