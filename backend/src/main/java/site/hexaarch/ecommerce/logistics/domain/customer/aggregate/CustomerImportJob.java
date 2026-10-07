package site.hexaarch.ecommerce.logistics.domain.customer.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerImportError;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户导入任务聚合根，负责记录一次批量导入的执行过程与结果反馈。
 *
 * @author kenyon
 */
@Getter
public class CustomerImportJob {

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
    private final List<CustomerImportError> errors;
    private final List<String> createdCustomerIds;
    private final List<String> updatedCustomerIds;

    private ImportJobStatus status;
    private int totalCount;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private String message;
    private LocalDateTime finishedAt;

    private CustomerImportJob(String id, String tenantId, String platform, String integrationId, String fileName,
                              LocalDateTime startedAt, LocalDateTime finishedAt, ImportJobStatus status,
                              int totalCount, int successCount, int failureCount, int skippedCount, String message,
                              List<CustomerImportError> errors, List<String> createdCustomerIds,
                              List<String> updatedCustomerIds) {
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
        this.createdCustomerIds = createdCustomerIds == null ? new ArrayList<>() : createdCustomerIds;
        this.updatedCustomerIds = updatedCustomerIds == null ? new ArrayList<>() : updatedCustomerIds;
    }

    public static CustomerImportJob create(String tenantId, String platform, String integrationId, String fileName) {
        return new CustomerImportJob(UUID.randomUUID().toString(), tenantId, platform, integrationId, fileName,
                LocalDateTime.now(), null, ImportJobStatus.PENDING, 0, 0, 0, 0, null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * 依据持久化结果还原任务实例（仓储读取时使用）
     */
    public static CustomerImportJob restore(String id, String tenantId, String platform, String integrationId,
                                            String fileName, LocalDateTime startedAt, LocalDateTime finishedAt,
                                            ImportJobStatus status, int totalCount, int successCount,
                                            int failureCount, int skippedCount, String message,
                                            List<CustomerImportError> errors, List<String> createdCustomerIds,
                                            List<String> updatedCustomerIds) {
        return new CustomerImportJob(id, tenantId, platform, integrationId, fileName, startedAt, finishedAt, status,
                totalCount, successCount, failureCount, skippedCount, message, errors, createdCustomerIds,
                updatedCustomerIds);
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
    public void recordSuccess(String customerId, boolean isUpdate) {
        this.successCount++;
        if (isUpdate) {
            this.updatedCustomerIds.add(customerId);
        } else {
            this.createdCustomerIds.add(customerId);
        }
    }

    /**
     * 记录一行失败
     */
    public void recordFailure(CustomerImportError error) {
        this.failureCount++;
        this.errors.add(error);
    }

    /**
     * 记录一行跳过（如重复且不允许更新）
     */
    public void recordSkipped(CustomerImportError error) {
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
            this.message = "成功导入 " + successCount + " 条客户";
        } else if (failureCount == 0) {
            this.status = ImportJobStatus.SUCCESS;
            this.message = "成功导入 " + successCount + " 条客户，跳过 " + skippedCount + " 条";
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
