package site.hexaarch.ecommerce.logistics.domain.finance.valueobject;

import lombok.Getter;

/**
 * 财务交易类型值对象，表示财务交易的类型。
 *
 * @author kenyon
 */
@Getter
public enum TransactionType {
    INCOME("income", "收入"),
    EXPENSE("expense", "支出"),
    REFUND("refund", "退款"),
    ADJUSTMENT("adjustment", "调整");

    private final String code;
    private final String description;

    TransactionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取类型编码。
     *
     * @return 类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取类型描述。
     *
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }
}
