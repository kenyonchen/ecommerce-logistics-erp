package site.hexaarch.ecommerce.logistics.domain.logistics.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 运费公式的常驻位（ISS-137 / R-55）：首重 + 续重×克 的全部算绪只允许写在
 * {@link LogisticsChannel#estimateFee(double)} 一处，试算端点与建单落库共用它。
 * <p>
 * 之所以单独钉住精度：{@code price_per_gram} 是 {@code DECIMAL(19,4)}（实测 0.0050/0.0150），
 * 而费用列是 {@code DECIMAL(19,2)}，所以"4 位费率 × 整数克 → 2 位 HALF_UP"这一步是唯一的
 * 取整点。取整模式一改，历史运费就会与新试算对不上。
 * </p>
 *
 * @author kenyon
 */
class LogisticsChannelFreightFormulaTest {

    private LogisticsChannel channel(String basePrice, String pricePerGram, String currency) {
        return LogisticsChannel.builder()
                .channelId("CH1")
                .channelCode("YTO")
                .channelName("圆通")
                .basePrice(new BigDecimal(basePrice))
                .pricePerGram(new BigDecimal(pricePerGram))
                .currency(currency)
                .build();
    }

    @Test
    @DisplayName("首重与续重分别按分取整，合计等于两者相加")
    void estimateFeeSplitsBaseAndWeightAndRoundsHalfUpToCents() {
        LogisticsFee fee = channel("8.00", "0.0050", "CNY").estimateFee(1681D);

        // 0.0050 × 1681 = 8.4050 → 按分 HALF_UP = 8.41（截断会得到 8.40，属于口径回归）
        assertEquals(new BigDecimal("8.00"), fee.getBaseFee());
        assertEquals(new BigDecimal("8.41"), fee.getWeightFee());
        assertEquals(new BigDecimal("16.41"), fee.getTotalFee());
        assertEquals("CNY", fee.getCurrency());
    }

    @Test
    @DisplayName("零克只收首重，负数克按零计费不产生负运费")
    void estimateFeeClampsNonPositiveWeight() {
        LogisticsFee zero = channel("8.00", "0.0150", "CNY").estimateFee(0D);
        assertEquals(new BigDecimal("0.00"), zero.getWeightFee());
        assertEquals(new BigDecimal("8.00"), zero.getTotalFee());

        LogisticsFee negative = channel("8.00", "0.0150", "CNY").estimateFee(-1200D);
        assertEquals(new BigDecimal("0.00"), negative.getWeightFee());
        assertEquals(new BigDecimal("8.00"), negative.getTotalFee());
    }

    @Test
    @DisplayName("费用币种跟随渠道，缺省为 CNY")
    void estimateFeeCarriesChannelCurrency() {
        assertEquals("USD", channel("1.00", "0.0050", "USD").estimateFee(100D).getCurrency());
    }
}
