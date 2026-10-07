package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量发货执行结果，逐单反馈成功/失败明细。
 *
 * @author kenyon
 */
@Data
public class BatchShipResultDto {
    private int totalCount;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private String message;
    private List<String> logisticsOrderIds = new ArrayList<>();
    private List<BatchShipFailureDto> failures = new ArrayList<>();

    public static BatchShipResultDto of(int totalCount) {
        BatchShipResultDto result = new BatchShipResultDto();
        result.setTotalCount(totalCount);
        return result;
    }

    public void recordSuccess(String logisticsOrderId) {
        successCount++;
        logisticsOrderIds.add(logisticsOrderId);
    }

    public void recordSkipped(String orderNumber, String reason) {
        skippedCount++;
        failures.add(BatchShipFailureDto.skipped(orderNumber, reason));
    }

    public void recordFailure(Integer rowNumber, String orderNumber, String reason) {
        failureCount++;
        failures.add(BatchShipFailureDto.failed(rowNumber, orderNumber, reason));
    }

    /**
     * 汇总提示文案
     */
    public void buildMessage() {
        if (failureCount == 0 && skippedCount == 0) {
            message = "批量发货完成，成功发货 " + successCount + " 单";
        } else if (failureCount == 0) {
            message = "批量发货完成，成功 " + successCount + " 单，跳过 " + skippedCount + " 单";
        } else if (successCount == 0) {
            message = "批量发货失败，共 " + failureCount + " 单未处理";
        } else {
            message = "批量发货部分成功，成功 " + successCount + " 单，失败 " + failureCount + " 单，跳过 "
                    + skippedCount + " 单";
        }
    }
}
