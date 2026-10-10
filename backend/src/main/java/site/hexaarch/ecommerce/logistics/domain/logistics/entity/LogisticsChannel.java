package site.hexaarch.ecommerce.logistics.domain.logistics.entity;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * 物流渠道实体，物流服务提供商，如顺丰、京东物流、FedEx、UPS等。
 *
 * @author kenyon
 */
@Getter
public class LogisticsChannel {
    private final String channelId;
    /**
     * 渠道编码，第三方平台/物流商侧的稳定标识（如 SF、YTO、JD），导入时按该编码匹配渠道
     */
    private final String channelCode;
    private final String channelName;
    private final String description;
    private final List<String> countries;
    /**
     * 首重基础费用
     */
    private final BigDecimal basePrice;
    /**
     * 每克续重费用
     */
    private final BigDecimal pricePerGram;
    /**
     * 计费币种
     */
    private final String currency;
    private final boolean active;

    public LogisticsChannel(String channelId, String channelCode, String channelName, String description,
                            List<String> countries, BigDecimal basePrice, BigDecimal pricePerGram, String currency,
                            boolean active) {
        this.channelId = channelId != null ? channelId : UUID.randomUUID().toString();
        this.channelCode = channelCode;
        this.channelName = channelName;
        this.description = description;
        this.countries = countries != null ? countries : List.of();
        this.basePrice = basePrice == null ? BigDecimal.ZERO : basePrice;
        this.pricePerGram = pricePerGram == null ? BigDecimal.ZERO : pricePerGram;
        this.currency = currency == null ? "CNY" : currency;
        this.active = active;
    }

    public static LogisticsChannel create(String channelCode, String channelName, String description,
                                          List<String> countries, BigDecimal basePrice, BigDecimal pricePerGram,
                                          String currency, boolean active) {
        return new LogisticsChannel(null, channelCode, channelName, description, countries, basePrice, pricePerGram,
                currency, active);
    }

    /**
     * 检查渠道是否支持指定国家。国家列表为空视为全球可用。
     *
     * @param country 国家代码
     * @return 如果支持则返回true，否则返回false
     */
    public boolean supportsCountry(String country) {
        if (countries.isEmpty()) {
            return true;
        }
        return countries.contains(country);
    }

    /**
     * 按本渠道的计费口径试算运费：首重 {@code basePrice} + 续重 {@code pricePerGram} × 克。
     * <p>
     * 这是全仓唯一的运费公式（{@code GET /api/logistics-channels/{id}/fee} 与建单落库共用），
     * 分两处写必然让"页面看到的"和"库里存的"对不上（ISS-137）。
     * 费率是 {@code DECIMAL(19,4)}、费用列是 {@code DECIMAL(19,2)}，首重与续重各自按分 HALF_UP 取整，
     * 因此 0.0050/克这类费率能按 4 位小数参与乘法后再取整，不会因为列宽被截成 0.01 而让运费翻倍。
     * </p>
     *
     * @param weightInGrams 计费重（克），与 {@code pricePerGram} 同单位；非正数按 0 计
     * @return 运费明细，币种跟随渠道
     */
    public LogisticsFee estimateFee(double weightInGrams) {
        BigDecimal weightFee = pricePerGram
                .multiply(BigDecimal.valueOf(Math.max(weightInGrams, 0D)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseFee = basePrice.setScale(2, RoundingMode.HALF_UP);
        return LogisticsFee.builder()
                .baseFee(baseFee)
                .weightFee(weightFee)
                .totalFee(baseFee.add(weightFee))
                .currency(currency)
                .build();
    }

    public LogisticsChannel activate() {
        return toBuilder().active(true).build();
    }

    public LogisticsChannel deactivate() {
        return toBuilder().active(false).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .channelId(channelId)
                .channelCode(channelCode)
                .channelName(channelName)
                .description(description)
                .countries(countries)
                .basePrice(basePrice)
                .pricePerGram(pricePerGram)
                .currency(currency)
                .active(active);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 物流渠道构建器。
     */
    public static class Builder {
        private String channelId;
        private String channelCode;
        private String channelName;
        private String description;
        private List<String> countries;
        private BigDecimal basePrice;
        private BigDecimal pricePerGram;
        private String currency;
        private boolean active = true;

        public Builder channelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder channelCode(String channelCode) {
            this.channelCode = channelCode;
            return this;
        }

        public Builder channelName(String channelName) {
            this.channelName = channelName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder countries(List<String> countries) {
            this.countries = countries;
            return this;
        }

        public Builder basePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public Builder pricePerGram(BigDecimal pricePerGram) {
            this.pricePerGram = pricePerGram;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public LogisticsChannel build() {
            return new LogisticsChannel(channelId, channelCode, channelName, description, countries, basePrice,
                    pricePerGram, currency, active);
        }
    }
}
