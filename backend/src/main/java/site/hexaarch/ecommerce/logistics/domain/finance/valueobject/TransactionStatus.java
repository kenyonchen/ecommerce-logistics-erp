package site.hexaarch.ecommerce.logistics.domain.finance.valueobject;

import lombok.Getter;

/**
 * 财务交易状态值对象，表示财务交易的状态。
 *
 * @author kenyon
 */
@Getter
public enum TransactionStatus {
    PENDING("pending", "待处理"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    TransactionStatus(String code, String description) {
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
}
