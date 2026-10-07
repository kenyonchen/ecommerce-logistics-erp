package site.hexaarch.ecommerce.logistics.interfaces.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据传输对象，用于向外部系统提供订单信息。
 * <p>
 * 注意：状态字段统一为 {@code status}，前端不再使用 {@code orderStatus}。
 * </p>
 *
 * @author kenyon
 */
@Data
@Schema(description = "订单信息")
public class OrderDto {
    @Schema(description = "订单ID")
    private String orderId;
    @Schema(description = "租户ID")
    private String tenantId;
    @Schema(description = "订单编号")
    private String orderNumber;
    @Schema(description = "客户ID")
    private String customerId;
    @Schema(description = "客户名称")
    private String customerName;
    @Schema(description = "客户邮箱")
    private String customerEmail;
    @Schema(description = "订单行项")
    private List<OrderItemDto> orderItems;
    @Schema(description = "收货地址")
    private ShippingAddress shippingAddress;
    @Schema(description = "付款信息")
    private PaymentInfo paymentInfo;
    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "订单状态")
    private OrderStatus status;
    @Schema(description = "订单状态中文名")
    private String statusLabel;
    @Schema(description = "可流转的下一状态")
    private List<String> allowedNextStatuses;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    @Schema(description = "来源平台（AMAZON/EBAY/SHOPIFY/MANUAL/FILE）")
    private String platform;
    @Schema(description = "第三方平台订单号")
    private String platformOrderId;

    /**
     * 由领域聚合填充派生字段（状态文案与可流转状态）
     */
    public void applyStatusDerivedFields(site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order order) {
        if (order == null || order.getOrderStatus() == null) {
            return;
        }
        this.statusLabel = order.getOrderStatus().getLabel();
        this.allowedNextStatuses = order.getOrderStatus().allowedNextStatuses()
                .stream()
                .map(Enum::name)
                .toList();
    }
}
