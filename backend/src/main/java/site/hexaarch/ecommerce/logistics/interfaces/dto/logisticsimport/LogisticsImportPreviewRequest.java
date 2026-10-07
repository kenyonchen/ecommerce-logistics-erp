package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;
import java.util.Map;

/**
 * 物流导入预览请求：
 * <ul>
 *     <li>来自第三方平台店铺：提供 integrationId（或 platform + useSampleData）拉取原始物流数据</li>
 *     <li>来自上传文件：提供 content（JSON/CSV文本）</li>
 * </ul>
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流导入预览请求")
public class LogisticsImportPreviewRequest {
    private String tenantId;
    @Schema(description = "第三方平台集成ID")
    private String integrationId;
    @Schema(description = "平台名称，文件上传导入时为 FILE")
    private String platform;
    @Schema(description = "按订单号/平台订单号过滤")
    private String orderNumber;
    @Schema(description = "按运单号过滤")
    private String trackingNumber;
    @Schema(description = "发货时间起（yyyy-MM-dd HH:mm:ss）")
    private String shippedFrom;
    @Schema(description = "发货时间止（yyyy-MM-dd HH:mm:ss）")
    private String shippedTo;
    @Schema(description = "拉取条数，默认50")
    private Integer limit;
    @Schema(description = "页码，从1开始")
    private Integer page;
    @Schema(description = "是否使用平台示例数据（未联调真实接口时使用）")
    private Boolean useSampleData;
    @Schema(description = "上传文件内容（JSON/CSV）")
    private String content;
    private String fileName;
    @Schema(description = "覆盖默认的字段映射关系")
    private List<FieldMappingDto> mappings;
    @Schema(description = "原始行（用于二次预览，避免重复拉取平台数据）")
    private List<Map<String, Object>> rawRows;
}
