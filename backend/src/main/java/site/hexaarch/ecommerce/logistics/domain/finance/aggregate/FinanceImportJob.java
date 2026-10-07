package site.hexaarch.ecommerce.logistics.domain.finance.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceImportError;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 财务数据导入任务聚合根，负责记录一次批量导入的执行过程与结果反馈。
 *
 * @author kenyon
 */
@Getter
public class FinanceImportJob {

    private final String id;
    private final String tenantId;
    /**
     * 平台名称，文件上传导入时为 FILE
     */
    private final String platform;
    /**
     * 关联的第三方平台集成ID
     */
    private final String integrationId;
    private final String fileName;
    private final LocalDateTime startedAt;
    private final List<FinanceImportError> errors;
    private final List<String> createdTransactionIds;
    private final List<String> updatedTransactionIds;

    private ImportJobStatus status;
    private int totalCount;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private String message;
    private LocalDateTime finishedAt;

    private FinanceImportJob(String id, String tenantId, String platform, String integrationId, String fileName,
                             LocalDateTime startedAt, LocalDateTime finishedAt, ImportJobStatus status,
                             int totalCount, int successCount, int failureCount, int skippedCount, String message,
                             List<FinanceImportError> errors, List<String> createdTransactionIds,
                             List<String> updatedTransactionIds) {
        this.id = id;
        this.tenantId = tenantId;
        this.platform = platform;
        this.integrationId = integrationId;
        this.fileName = fileName;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.skippedCount = skippedCount;
        this.message = message;
        this.errors = errors == null ? new ArrayList<>() : errors;
        this.createdTransactionIds = createdTransactionIds == null ? new ArrayList<>() : createdTransactionIds;
        this.updatedTransactionIds = updatedTransactionIds == null ? new ArrayList<>() : updatedTransactionIds;
    }

    public static FinanceImportJob create(String tenantId, String platform, String integrationId, String fileName) {
        return new FinanceImportJob(UUID.randomUUID().toString(), tenantId, platform, integrationId, fileName,
                LocalDateTime.now(), null, ImportJobStatus.PENDING, 0, 0, 0, 0, null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * 依据持久化结果还原任务实例（仓储读取时使用）
     */
    public static FinanceImportJob restore(String id, String tenantId, String platform, String integrationId,
                                           String fileName, LocalDateTime startedAt, LocalDateTime finishedAt,
                                           ImportJobStatus status, int totalCount, int successCount,
                                           int failureCount, int skippedCount, String message,
                                           List<FinanceImportError> errors, List<String> createdTransactionIds,
                                           List<String> updatedTransactionIds) {
        return new FinanceImportJob(id, tenantId, platform, integrationId, fileName, startedAt, finishedAt, status,
                totalCount, successCount, failureCount, skippedCount, message, errors, createdTransactionIds,
                updatedTransactionIds);
    }

    /**
     * 任务开始执行
     */
    public void start(int totalCount) {
        this.status = ImportJobStatus.RUNNING;
        this.totalCount = totalCount;
    }

    /**
     * 记录一行成功（新增或更新）
     */
    public void recordSuccess(String transactionId, boolean isUpdate) {
        this.successCount++;
        if (isUpdate) {
            this.updatedTransactionIds.add(transactionId);
        } else {
            this.createdTransactionIds.add(transactionId);
        }
    }

    /**
     * 记录一行失败
     */
    public void recordFailure(FinanceImportError error) {
        this.failureCount++;
        this.errors.add(error);
    }

    /**
     * 记录一行跳过（如重复且不允许更新）
     */
    public void recordSkipped(FinanceImportError error) {
        this.skippedCount++;
        if (error != null) {
            this.errors.add(error);
        }
    }

    /**
     * 标记任务结束并计算最终状态
     */
    public void complete() {
        this.finishedAt = LocalDateTime.now();
        if (failureCount == 0 && skippedCount == 0) {
            this.status = ImportJobStatus.SUCCESS;
            this.message = "成功导入 " + successCount + " 条财务流水";
        } else if (failureCount == 0) {
            this.status = ImportJobStatus.SUCCESS;
            this.message = "成功导入 " + successCount + " 条财务流水，跳过 " + skippedCount + " 条";
        } else if (successCount == 0) {
            this.status = ImportJobStatus.FAILED;
            this.message = "导入失败，失败 " + failureCount + " 条";
        } else {
            this.status = ImportJobStatus.PARTIAL_SUCCESS;
            this.message = "成功 " + successCount + " 条，失败 " + failureCount + " 条，跳过 " + skippedCount + " 条";
        }
    }

    /**
     * 标记任务整体失败（如拉取平台数据异常）
     */
    public void fail(String errorMessage) {
        this.status = ImportJobStatus.FAILED;
        this.message = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
