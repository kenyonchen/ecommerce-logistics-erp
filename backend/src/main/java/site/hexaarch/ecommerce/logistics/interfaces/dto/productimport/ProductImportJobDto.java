package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

/**
 * 导入任务结果明细DTO，既用于导入执行结果反馈，也用于任务列表/详情查询。
 *
 * @author kenyon
 */
@Data
public class ProductImportJobDto {
    private String jobId;
    private String tenantId;
    private String platform;
    private String integrationId;
    private String fileName;
    private String status;
    private String statusLabel;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Integer skippedCount;
    private String message;
    private String startedAt;
    private String finishedAt;
    private java.util.List<ProductImportErrorDto> errors;
    private java.util.List<String> createdProductIds;
    private java.util.List<String> updatedProductIds;
}
