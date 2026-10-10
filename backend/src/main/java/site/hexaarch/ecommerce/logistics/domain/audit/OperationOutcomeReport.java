package site.hexaarch.ecommerce.logistics.domain.audit;

/**
 * 「结果自报」契约：响应是 {@code HTTP 200 + Result.code=200}，但业务上到底有没有成，
 * 只有载荷自己知道（连通性测试一类的 {@code success=false}）。
 * <p>
 * 与 {@code UnfinishedRowReport}（行级未生效）同一动机：只看状态码会让"探活失败"与
 * "探活通过"留下两条一模一样的 SUCCESS 留痕，取证时无法区分（ISS-130）。响应契约本身不改
 * （测试不通过是预期分支，前端要靠 {@code data.success} 与 {@code data.message} 回显原因），
 * 改由载荷自己报告失败原因，审计据此才允许记 SUCCESS。
 * </p>
 *
 * @author kenyon
 */
public interface OperationOutcomeReport {

    /**
     * @return 失败原因（写进审计 {@code error_message}）；操作确实成功时返回 {@code null}
     */
    String failureReason();
}
