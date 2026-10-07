package site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;

import java.util.List;
import java.util.Map;

/**
 * 客户导入预览请求：
 * <ul>
 *     <li>来自第三方平台：提供 integrationId（或 platform + useSampleData）拉取原始数据</li>
 *     <li>来自上传文件：提供 content（JSON/CSV文本）</li>
 * </ul>
 *
 * @author kenyon
 */
@Data
public class CustomerImportPreviewRequest {
    private String tenantId;
    /**
     * 第三方平台集成ID
     */
    private String integrationId;
    /**
     * 平台名称，文件上传导入时可传 FILE
     */
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
    /**
     * 增量拉取：平台侧更新时间起（yyyy-MM-dd HH:mm:ss）
     */
    private String updatedFrom;
    /**
     * 增量拉取：平台侧更新时间止（yyyy-MM-dd HH:mm:ss）
     */
    private String updatedTo;
    /**
     * 拉取条数，默认50
     */
    private Integer limit;
    /**
     * 页码，从1开始
     */
    private Integer page;
    /**
     * 是否使用平台示例数据（未联调真实接口时使用）
     */
    private Boolean useSampleData;
    /**
     * 上传文件内容（JSON/CSV）
     */
    private String content;
    private String fileName;
    /**
     * 覆盖默认的字段映射关系（用户在UI上手工调整后预览）
     */
    private List<FieldMappingDto> mappings;
    /**
     * 原始行（用于二次预览，避免重复拉取平台数据）
     */
    private List<Map<String, Object>> rawRows;
}
