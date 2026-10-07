package site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;

/**
 * 财务数据批量导入执行请求。
 * rows 为空时会依据 platform/integrationId/useSampleData 重新拉取数据后再导入。
 *
 * @author kenyon
 */
@Data
public class FinanceImportRequest {
    private String tenantId;
    private String integrationId;
    private String platform;
    /**
     * 待导入的标准财务流水行（来自预览结果）
     */
    private List<FinanceImportRowDto> rows;
    /**
     * 平台交易流水号已存在时是否更新已有财务流水，默认 true
     */
    private Boolean updateExisting;
    /**
     * 是否与第三方平台交易流水号建立幂等关联（默认 true）
     */
    private Boolean enableIdempotency;
    /**
     * rows 为空时的补充拉取参数
     */
    private String externalTransactionId;
    private String updatedFrom;
    private String updatedTo;
    private Integer limit;
    private Integer page;
    private Boolean useSampleData;
    private List<FieldMappingDto> mappings;
    private String fileName;
}
