package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标准化后的平台物流行DTO（导入预览与导入执行共用）
 *
 * @author kenyon
 */
@Data
@Schema(description = "导入物流行")
public class LogisticsImportRowDto {
    @Schema(description = "行号（从1开始）")
    private Integer rowNumber;
    @Schema(description = "平台物流单号（幂等去重键）")
    private String externalLogisticsId;
    @Schema(description = "运单号")
    private String trackingNumber;
    @Schema(description = "系统订单号")
    private String orderNumber;
    @Schema(description = "平台订单号")
    private String externalOrderId;
    @Schema(description = "物流渠道编码")
    private String channelCode;
    @Schema(description = "承运商")
    private String carrier;
    @Schema(description = "物流状态")
    private String status;
    @Schema(description = "物流跟踪链接")
    private String trackingUrl;
    @Schema(description = "面单下载地址")
    private String labelUrl;
    @Schema(description = "面单格式")
    private String labelFormat;
    @Schema(description = "条形码")
    private String barcode;
    @Schema(description = "发货时间")
    private LocalDateTime shippedAt;
    @Schema(description = "预计送达时间")
    private LocalDateTime estimatedArrivalAt;
    @Schema(description = "签收时间")
    private LocalDateTime deliveredAt;
    @Schema(description = "物流费用")
    private BigDecimal totalFee;
    @Schema(description = "币种")
    private String currency;
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
    @Schema(description = "物流轨迹")
    private List<LogisticsImportTraceDto> traces;
    @Schema(description = "字段级错误明细")
    private List<LogisticsImportErrorDto> errors;
}
