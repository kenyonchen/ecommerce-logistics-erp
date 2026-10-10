package site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport;

import lombok.Data;

import site.hexaarch.ecommerce.logistics.interfaces.common.UnfinishedRowReport;

import java.util.List;

/**
 * 财务导入任务结果明细DTO，既用于导入执行结果反馈，也用于任务列表/详情查询。
 *
 * @author kenyon
 */
@Data
public class FinanceImportJobDto implements UnfinishedRowReport {
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

    /**
     * 审计留痕用（ISS-117 / R-36 / R-38）：导入作业允许"HTTP 200 + code=200 却有一行没落库"，
     * 这种形状必须报出来，否则只落了 3 行的作业会在留痕里记成整批成功。
     */
    @Override
    public String unfinishedRows() {
        String firstDetail = errors == null || errors.isEmpty() ? null
                : UnfinishedRowReport.firstRowDetail(errors.get(0).getRowNumber(), errors.get(0).getMessage());
        return UnfinishedRowReport.summarize(totalCount, successCount, failureCount, skippedCount, firstDetail);
    }
}
