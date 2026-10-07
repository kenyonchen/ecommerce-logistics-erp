package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标准化后的平台订单行（导入中间模型）。
 *
 * @param rowNumber        在原始数据中的行号（从1开始）
 * @param externalOrderId  平台订单号（幂等去重键）
 * @param orderNumber      系统订单号（可空，缺失时自动生成）
 * @param customerId       客户ID
 * @param customerName     客户名称
 * @param customerEmail    客户邮箱
 * @param status           订单状态
 * @param currency         币种
 * @param orderedAt        下单时间
 * @param paymentMethod    支付方式
 * @param paymentStatus    支付状态
 * @param transactionId    交易号
 * @param totalAmount      订单总金额（缺失时按行项合计）
 * @param recipient        收件人
 * @param phone            联系电话
 * @param country          国家
 * @param province         省/州
 * @param city             城市
 * @param street           详细地址
 * @param zipCode          邮编
 * @param items            订单行项
 * @author kenyon
 */
public record ImportedOrderRow(int rowNumber,
                               String externalOrderId,
                               String orderNumber,
                               String customerId,
                               String customerName,
                               String customerEmail,
                               OrderStatus status,
                               String currency,
                               LocalDateTime orderedAt,
                               String paymentMethod,
                               String paymentStatus,
                               String transactionId,
                               BigDecimal totalAmount,
                               String recipient,
                               String phone,
                               String country,
                               String province,
                               String city,
                               String street,
                               String zipCode,
                               List<ImportedOrderItem> items) {

    /**
     * 行项合计金额
     */
    public BigDecimal itemsTotal() {
        if (items == null) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(ImportedOrderItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 订单金额：优先取平台给出的总金额，缺失时按行项合计
     */
    public BigDecimal effectiveTotal() {
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            return totalAmount;
        }
        return itemsTotal();
    }
}
