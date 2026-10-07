package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;

/**
 * 批量导入物流单执行请求。
 * rows 为空时会依据 platform/integrationId/useSampleData 重新拉取数据后再导入。
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流批量导入请求")
public class LogisticsImportRequest {
    private String tenantId;
    private String integrationId;
    private String platform;
    @Schema(description = "待导入的标准物流行（来自预览结果）")
    private List<LogisticsImportRowDto> rows;
    @Schema(description = "平台物流单号已存在时是否更新已有物流单，默认 true")
    private Boolean updateExisting;
    @Schema(description = "是否按平台物流单号幂等去重，默认 true")
    private Boolean enableIdempotency;
    @Schema(description = "rows 为空时的补充拉取参数")
    private String orderNumber;
    private String trackingNumber;
    private String shippedFrom;
    private String shippedTo;
    private Integer limit;
    private Integer page;
    private Boolean useSampleData;
    private List<FieldMappingDto> mappings;
    private String fileName;
}
