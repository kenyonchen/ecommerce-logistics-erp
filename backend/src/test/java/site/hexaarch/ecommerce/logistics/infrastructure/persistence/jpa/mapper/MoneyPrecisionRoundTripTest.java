package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsChannelJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsFeeJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.SkuJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics.LogisticsChannelMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics.LogisticsOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapper;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderItemDto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金额在"领域 ↔ 持久化 ↔ 对外DTO"三跳上的精度往返测试（ISS-023）。
 * <p>
 * 修类型只是把 {@code Double} 换成 {@code BigDecimal}，真正要钉住的是<b>换算有没有发生</b>：
 * 只要链路上任何一跳写 {@code .doubleValue()} 或 {@code BigDecimal.valueOf(someDouble)}，
 * 分位与标度就会在那里掉一次，之后再用 BigDecimal 也只是把噪声原样写进库。
 * 修复前实测到的产物是商品总价 {@code 828.399999999999}（应为 {@code 828.40}）。
 * </p>
 * <p>
 * 断言刻意压在 {@code equals} 与 {@code scale()} 上而不是 {@code compareTo}：
 * {@code 0.0050} 与 {@code 0.005} 数值相等但标度不同，只有后者能证明 {@code DECIMAL(19,4)}
 * 的费率没被 double 收成三位（R-52）。
 * </p>
 *
 * @author kenyon
 */
class MoneyPrecisionRoundTripTest {

    private final LogisticsOrderMapper logisticsOrderMapper = Mappers.getMapper(LogisticsOrderMapper.class);
    private final LogisticsChannelMapper logisticsChannelMapper = Mappers.getMapper(LogisticsChannelMapper.class);
    private final ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);
    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("物流费用：领域 -> 实体 -> 领域，四段金额逐位不变")
    void logisticsFeeKeepsEveryDecimalPlace() {
        LogisticsFee fee = LogisticsFee.builder()
                .feeId("fee-1")
                .baseFee(new BigDecimal("8.40"))
                .weightFee(new BigDecimal("0.0050"))
                .totalFee(new BigDecimal("8.4050"))
                .currency("CNY")
                .build();

        LogisticsFeeJpaEntity entity = logisticsOrderMapper.toJpaEntity(fee);
        assertSame(fee.getTotalFee(), entity.getTotalFee(), "实体应持有同一个 BigDecimal，不许过 double");
        assertEquals(2, entity.getBaseFee().scale());

        LogisticsFee restored = logisticsOrderMapper.toDomainFee(entity);
        assertEquals(new BigDecimal("8.40"), restored.getBaseFee());
        assertEquals(new BigDecimal("0.0050"), restored.getWeightFee());
        assertEquals(new BigDecimal("8.4050"), restored.getTotalFee());
        assertEquals(4, restored.getWeightFee().scale(), "0.0050 被收成 0.005 就说明中间过了 double");
    }

    @Test
    @DisplayName("渠道费率：首重价与每克价按 19,2 / 19,4 原样往返")
    void channelPricesKeepConfiguredScale() {
        LogisticsChannel channel = LogisticsChannel.builder()
                .channelId("channel-1")
                .channelCode("YTO")
                .channelName("圆通")
                .basePrice(new BigDecimal("8.40"))
                .pricePerGram(new BigDecimal("0.0050"))
                .currency("CNY")
                .active(true)
                .build();

        LogisticsChannelJpaEntity entity = logisticsChannelMapper.toJpaEntity(channel);
        assertEquals(new BigDecimal("0.0050"), entity.getPricePerGram());

        LogisticsChannel restored = logisticsChannelMapper.toDomainEntity(entity);
        assertEquals(new BigDecimal("8.40"), restored.getBasePrice());
        assertEquals(new BigDecimal("0.0050"), restored.getPricePerGram());
        assertEquals(4, restored.getPricePerGram().scale());
    }

    @Test
    @DisplayName("SKU 售价与成本价：实体 -> ProductPrice 不做浮点中转")
    void skuPricesAreNotRebuiltFromDouble() {
        SkuJpaEntity entity = new SkuJpaEntity();
        entity.setSellingPrice(new BigDecimal("1299.00"));
        entity.setCostPrice(new BigDecimal("828.40"));

        ProductPrice price = productMapper.toProductPrice(entity);
        assertSame(entity.getSellingPrice(), price.getSellingPrice());
        assertEquals(new BigDecimal("828.40"), price.getCostPrice(), "成本价被复述成 828.399999999999 即为回归");
    }

    @Test
    @DisplayName("订单行项DTO：金额保持 BigDecimal 且 JSON 逐位输出")
    void orderItemDtoEmitsExactDecimalMoney() throws Exception {
        OrderItem item = OrderItem.builder()
                .id("item-1")
                .productId("product-1")
                .sku("SKU-1")
                .productName("商品")
                .quantity(4)
                .unitPrice(new BigDecimal("207.10"))
                .totalPrice(new BigDecimal("828.40"))
                .build();

        OrderItemDto dto = orderMapper.toDtoItem(item);
        assertEquals(new BigDecimal("828.40"), dto.getTotalPrice());
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("\"totalPrice\":828.40"), "实际 JSON: " + json);
        assertTrue(json.contains("\"unitPrice\":207.10"), "实际 JSON: " + json);
    }
}
