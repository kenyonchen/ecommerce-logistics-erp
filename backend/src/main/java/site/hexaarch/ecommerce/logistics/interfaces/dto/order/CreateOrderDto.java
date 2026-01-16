package site.hexaarch.ecommerce.logistics.interfaces.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;

import java.util.List;

/**
 * 创建订单DTO，用于接收创建订单的请求参数。
 *
 * @author kenyon
 */
@Data
@Schema(description = "创建订单DTO")
public class CreateOrderDto {
    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TN-111111", minLength = 2, maxLength = 20)
    private String orderNumber;
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "A12345", minLength = 2, maxLength = 20)
    private String customerId;
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三", minLength = 2, maxLength = 32)
    private String customerName;
    @Schema(description = "客户邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "aa@bb.cc", minLength = 6, maxLength = 64)
    private String customerEmail;
    @Schema(description = "收货地址")
    private ShippingAddress shippingAddress;
    @Schema(description = "支付信息")
    private PaymentInfo paymentInfo;
    @Schema(description = "订单子项")
    private List<OrderItemDto> orderItems;
}
