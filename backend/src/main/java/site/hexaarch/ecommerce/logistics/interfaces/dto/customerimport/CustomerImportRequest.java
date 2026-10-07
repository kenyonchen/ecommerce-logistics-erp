package site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;

/**
 * 客户批量导入执行请求。
 * rows 为空时会依据 platform/integrationId/useSampleData 重新拉取数据后再导入。
 *
 * @author kenyon
 */
@Data
public class CustomerImportRequest {
    private String tenantId;
    private String integrationId;
    private String platform;
    /**
     * 数据来源类型：CUSTOMERS（平台客户接口）| ORDERS（从平台订单数据中萃取买家），默认 CUSTOMERS
     */
    private String sourceType;
    /**
     * 拉取时按买家关键字过滤
     */
    private String keyword;
    /**
     * sourceType=ORDERS 时，拉取订单按平台订单号过滤
     */
    private String orderNumber;
    private String updatedFrom;
    private String updatedTo;
    private Integer limit;
    private Integer page;
    private Boolean useSampleData;
    /**
     * 待导入的标准客户行（来自预览结果）
     */
    private List<CustomerImportRowDto> rows;
    /**
     * 平台买家已存在时是否更新已有客户，默认 true
     */
    private Boolean updateExisting;
    /**
     * 是否与第三方平台买家ID建立幂等关联（默认 true）
     */
    private Boolean enableIdempotency;
    private List<FieldMappingDto> mappings;
    private String fileName;
}
