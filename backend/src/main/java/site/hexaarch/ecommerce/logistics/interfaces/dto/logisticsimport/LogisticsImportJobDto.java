package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import site.hexaarch.ecommerce.logistics.interfaces.common.UnfinishedRowReport;

import java.util.List;

/**
 * 物流导入任务结果明细DTO，既用于导入执行结果反馈，也用于任务列表/详情查询。
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流导入任务")
public class LogisticsImportJobDto implements UnfinishedRowReport {
    private String jobId;
    private String tenantId;
    private String platform;
    private String integrationId;
    private String fileName;
    @Schema(description = "任务状态：PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED")
    private String status;
    private String statusLabel;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Integer skippedCount;
    @Schema(description = "结果汇总说明")
    private String message;
    private String startedAt;
    private String finishedAt;
    @Schema(description = "行级错误明细")
    private List<LogisticsImportErrorDto> errors;
    @Schema(description = "本次新增的物流单ID")
    private List<String> createdLogisticsOrderIds;
    @Schema(description = "本次更新的物流单ID")
    private List<String> updatedLogisticsOrderIds;

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
