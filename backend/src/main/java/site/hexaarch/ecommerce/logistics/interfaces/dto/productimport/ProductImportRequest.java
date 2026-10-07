package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

import java.util.List;

/**
 * 批量导入执行请求。
 * rows 为空时会依据 platform/integrationId/useSampleData 重新拉取数据后再导入。
 *
 * @author kenyon
 */
@Data
public class ProductImportRequest {
    private String tenantId;
    private String integrationId;
    private String platform;
    /**
     * 待导入的标准商品行（来自预览结果）
     */
    private List<ProductImportRowDto> rows;
    /**
     * SKU编码已存在时是否更新已有商品，默认 true
     */
    private Boolean updateExisting;
    /**
     * 是否与第三方来源ID建立幂等关联（默认 true）
     */
    private Boolean enableIdempotency;
    /**
     * rows 为空时的补充拉取参数
     */
    private String keyword;
    private Integer limit;
    private Boolean useSampleData;
    private List<FieldMappingDto> mappings;
    private String fileName;
}
