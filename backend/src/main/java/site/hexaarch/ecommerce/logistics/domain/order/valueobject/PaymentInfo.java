package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 付款信息值对象，描述付款方式和状态。
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
public class PaymentInfo {
    @Schema(description = "支付方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "CREDIT_CARD")
    private final String paymentMethod;
    @Schema(description = "支付状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "SUCCESS")
    private final String paymentStatus;
    @Schema(description = "交易ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "TRX123456789")
    private final String transactionId;
    @Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "99.99")
    private final BigDecimal amount;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param paymentMethod 付款方式
     * @param paymentStatus 付款状态
     * @param transactionId 交易ID
     * @param amount        付款金额
     */
    private PaymentInfo(String paymentMethod, String paymentStatus, String transactionId, BigDecimal amount) {
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Payment method cannot be null");
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "Payment status cannot be null");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        if (this.amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentInfo that = (PaymentInfo) o;
        return Objects.equals(paymentMethod, that.paymentMethod) &&
                Objects.equals(paymentStatus, that.paymentStatus) &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentMethod, paymentStatus, transactionId, amount);
    }

    // 手动添加getter方法，确保编译器能找到
    public BigDecimal getAmount() {
        return amount;
    }
}