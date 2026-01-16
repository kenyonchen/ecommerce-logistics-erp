package site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject;

import lombok.Getter;

/**
 * 库存状态值对象，表示库存的当前状态。
 *
 * @author kenyon
 */
@Getter
public enum InventoryStatus {
    NORMAL("normal", "正常"),
    SHORTAGE("shortage", "不足"),
    EXCESS("excess", "过剩"),
    OUT_OF_STOCK("out_of_stock", "缺货");

    private final String code;
    private final String description;

    // 手动添加构造器，解决Lombok在枚举中无法正常生成构造器的问题
    InventoryStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 检查库存是否为正常状态。
     *
     * @return 如果库存状态为正常则返回true，否则返回false
     */
    public boolean isNormal() {
        return this == NORMAL;
    }

    /**
     * 检查库存是否为不足状态。
     *
     * @return 如果库存状态为不足则返回true，否则返回false
     */
    public boolean isShortage() {
        return this == SHORTAGE;
    }

    /**
     * 检查库存是否为过剩状态。
     *
     * @return 如果库存状态为过剩则返回true，否则返回false
     */
    public boolean isExcess() {
        return this == EXCESS;
    }

    /**
     * 检查库存是否为缺货状态。
     *
     * @return 如果库存状态为缺货则返回true，否则返回false
     */
    public boolean isOutOfStock() {
        return this == OUT_OF_STOCK;
    }
}