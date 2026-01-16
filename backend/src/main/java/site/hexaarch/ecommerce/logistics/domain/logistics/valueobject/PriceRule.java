package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 价格规则值对象，用于计算物流费用。
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
public class PriceRule {
    private final String ruleId;
    private final String country;
    private final WeightRange weightRange;
    private final VolumeRange volumeRange;
    private final BigDecimal price;
    private final String currency;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param ruleId      规则ID
     * @param country     国家代码
     * @param weightRange 重量范围
     * @param volumeRange 体积范围
     * @param price       价格
     * @param currency    货币类型
     */
    private PriceRule(String ruleId, String country, WeightRange weightRange, VolumeRange volumeRange, BigDecimal price, String currency) {
        this.ruleId = ruleId;
        this.country = Objects.requireNonNull(country, "Country cannot be null");
        this.weightRange = weightRange;
        this.volumeRange = volumeRange;
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.currency = currency != null ? currency : "CNY";
    }

    /**
     * 检查规则是否适用于指定的重量和体积。
     *
     * @param weight 重量
     * @param volume 体积
     * @return 如果适用则返回true，否则返回false
     */
    public boolean isApplicable(double weight, double volume) {
        boolean weightMatch = this.weightRange == null || this.weightRange.isInRange(weight);
        boolean volumeMatch = this.volumeRange == null || this.volumeRange.isInRange(volume);
        return weightMatch && volumeMatch;
    }

    /**
     * 获取价格
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 获取货币类型
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * 重量范围值对象。
     */
    @Getter
    @Builder(toBuilder = true)
    public static class WeightRange {
        private final double minWeight;
        private final double maxWeight;
        private final String unit;

        /**
         * 构造函数，确保所有必填字段都不为空。
         *
         * @param minWeight 最小重量
         * @param maxWeight 最大重量
         * @param unit      重量单位
         */
        private WeightRange(double minWeight, double maxWeight, String unit) {
            this.minWeight = minWeight >= 0 ? minWeight : 0;
            this.maxWeight = maxWeight > minWeight ? maxWeight : Double.MAX_VALUE;
            this.unit = unit != null ? unit : "kg";
        }

        /**
         * 检查重量是否在范围内。
         *
         * @param weight 重量
         * @return 如果重量在范围内则返回true，否则返回false
         */
        public boolean isInRange(double weight) {
            return weight >= this.minWeight && weight <= this.maxWeight;
        }
    }

    /**
     * 体积范围值对象。
     */
    @Getter
    @Builder(toBuilder = true)
    public static class VolumeRange {
        private final double minVolume;
        private final double maxVolume;
        private final String unit;

        /**
         * 构造函数，确保所有必填字段都不为空。
         *
         * @param minVolume 最小体积
         * @param maxVolume 最大体积
         * @param unit      体积单位
         */
        private VolumeRange(double minVolume, double maxVolume, String unit) {
            this.minVolume = minVolume >= 0 ? minVolume : 0;
            this.maxVolume = maxVolume > minVolume ? maxVolume : Double.MAX_VALUE;
            this.unit = unit != null ? unit : "cm³";
        }

        /**
         * 检查体积是否在范围内。
         *
         * @param volume 体积
         * @return 如果体积在范围内则返回true，否则返回false
         */
        public boolean isInRange(double volume) {
            return volume >= this.minVolume && volume <= this.maxVolume;
        }
    }
}