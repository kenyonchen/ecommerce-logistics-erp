package site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标准化后的平台订单行DTO（导入预览与导入执行共用）
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入订单行")
public class OrderImportRowDto {
    @Schema(description = "行号（从1开始）")
    private Integer rowNumber;
    @Schema(description = "平台订单号（幂等去重键）")
    private String externalOrderId;
    @Schema(description = "系统订单号")
    private String orderNumber;
    @Schema(description = "客户ID")
    private String customerId;
    @Schema(description = "客户名称")
    private String customerName;
    @Schema(description = "客户邮箱")
    private String customerEmail;
    @Schema(description = "订单状态")
    private String status;
    @Schema(description = "币种")
    private String currency;
    @Schema(description = "下单时间")
    private LocalDateTime orderedAt;
    @Schema(description = "支付方式")
    private String paymentMethod;
    @Schema(description = "支付状态")
    private String paymentStatus;
    @Schema(description = "交易号")
    private String transactionId;
    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;
    @Schema(description = "收件人")
    private String recipient;
    @Schema(description = "联系电话")
    private String phone;
    @Schema(description = "国家")
    private String country;
    @Schema(description = "省/州")
    private String province;
    @Schema(description = "城市")
    private String city;
    @Schema(description = "详细地址")
    private String street;
    @Schema(description = "邮编")
    private String zipCode;
    @Schema(description = "订单行项")
    private List<OrderImportItemDto> items;
    @Schema(description = "字段级错误明细")
    private List<OrderImportErrorDto> errors;
}
