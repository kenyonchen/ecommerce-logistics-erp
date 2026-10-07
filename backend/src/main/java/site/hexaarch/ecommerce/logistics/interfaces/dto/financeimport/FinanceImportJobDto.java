package site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport;

import lombok.Data;

import java.util.List;

/**
 * 财务导入任务结果明细DTO，既用于导入执行结果反馈，也用于任务列表/详情查询。
 *
 * @author kenyon
 */
@Data
public class FinanceImportJobDto {
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
    private List<FinanceImportErrorDto> errors;
    private List<String> createdTransactionIds;
    private List<String> updatedTransactionIds;
}
