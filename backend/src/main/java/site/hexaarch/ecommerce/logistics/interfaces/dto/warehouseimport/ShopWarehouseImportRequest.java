package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 批量导入店铺仓库执行请求。
 * rows 为空时会依据 platform/integrationId/useSampleData 重新拉取数据后再导入。
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseImportRequest {
    private String tenantId;
    private String integrationId;
    private String platform;
    /**
     * 待导入的标准店铺仓库行（来自预览结果）
     */
    private java.util.List<ShopWarehouseImportRowDto> rows;
    /**
     * 平台仓库ID已存在时是否更新已有绑定，默认 true
     */
    private Boolean updateExisting;
    /**
     * rows 为空时的补充拉取参数
     */
    private String keyword;
    private Integer limit;
    private Boolean useSampleData;
    private java.util.List<ShopWarehouseFieldMappingDto> mappings;
    private String fileName;
}
