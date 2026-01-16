package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 物流费用值对象，描述物流服务的费用。
 *
 * @author kenyon
 */
@Getter
public class LogisticsFee {
    private final String feeId;
    private final BigDecimal baseFee;
    private final BigDecimal weightFee;
    private final BigDecimal volumeFee;
    private final BigDecimal totalFee;
    private final String currency;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param feeId     费用ID
     * @param baseFee   基础费用
     * @param weightFee 重量费用
     * @param volumeFee 体积费用
     * @param totalFee  总费用
     * @param currency  货币类型
     */
    public LogisticsFee(String feeId, BigDecimal baseFee, BigDecimal weightFee, BigDecimal volumeFee, BigDecimal totalFee, String currency) {
        this.feeId = feeId;
        this.baseFee = baseFee != null ? baseFee : BigDecimal.ZERO;
        this.weightFee = weightFee != null ? weightFee : BigDecimal.ZERO;
        this.volumeFee = volumeFee != null ? volumeFee : BigDecimal.ZERO;
        this.totalFee = totalFee != null ? totalFee : calculateTotalFee(this.baseFee, this.weightFee, this.volumeFee);
        this.currency = currency != null ? currency : "CNY";
    }

    public static LogisticsFeeBuilder builder() {
        return new LogisticsFeeBuilder();
    }

    /**
     * 计算总费用。
     *
     * @param baseFee   基础费用
     * @param weightFee 重量费用
     * @param volumeFee 体积费用
     * @return 总费用
     */
    private BigDecimal calculateTotalFee(BigDecimal baseFee, BigDecimal weightFee, BigDecimal volumeFee) {
        return baseFee.add(weightFee).add(volumeFee);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogisticsFee that = (LogisticsFee) o;
        return Objects.equals(feeId, that.feeId) &&
                Objects.equals(totalFee, that.totalFee) &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feeId, totalFee, currency);
    }

    public static class LogisticsFeeBuilder {
        private String feeId;
        private BigDecimal baseFee;
        private BigDecimal weightFee;
        private BigDecimal volumeFee;
        private BigDecimal totalFee;
        private String currency;

        public LogisticsFeeBuilder feeId(String feeId) {
            this.feeId = feeId;
            return this;
        }

        public LogisticsFeeBuilder baseFee(BigDecimal baseFee) {
            this.baseFee = baseFee;
            return this;
        }

        public LogisticsFeeBuilder weightFee(BigDecimal weightFee) {
            this.weightFee = weightFee;
            return this;
        }

        public LogisticsFeeBuilder volumeFee(BigDecimal volumeFee) {
            this.volumeFee = volumeFee;
            return this;
        }

        public LogisticsFeeBuilder totalFee(BigDecimal totalFee) {
            this.totalFee = totalFee;
            return this;
        }

        public LogisticsFeeBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public LogisticsFee build() {
            return new LogisticsFee(feeId, baseFee, weightFee, volumeFee, totalFee, currency);
        }
    }
}