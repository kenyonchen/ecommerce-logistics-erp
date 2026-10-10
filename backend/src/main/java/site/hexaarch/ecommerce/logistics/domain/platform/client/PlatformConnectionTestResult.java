package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.audit.OperationOutcomeReport;

/**
 * 连通性测试结果。
 * <p>
 * 实现 {@link OperationOutcomeReport}：探活失败是走 {@code data.success=false} 回来的（HTTP 与
 * {@code Result.code} 都是 200），不接进审计判定就会留一条"测试通过"的假 SUCCESS（ISS-130）。
 * </p>
 *
 * @param success     凭据与地址是否可用
 * @param adapterType 实际执行测试的适配器注册键
 * @param latencyMs   外呼耗时
 * @param message     失败原因或成功时的店铺标识摘要（不得包含令牌明文）
 * @author kenyon
 */
public record PlatformConnectionTestResult(boolean success, String adapterType, long latencyMs, String message)
        implements OperationOutcomeReport {

    public static PlatformConnectionTestResult ok(String adapterType, long latencyMs, String message) {
        return new PlatformConnectionTestResult(true, adapterType, latencyMs, message);
    }

    public static PlatformConnectionTestResult failed(String adapterType, long latencyMs, String message) {
        return new PlatformConnectionTestResult(false, adapterType, latencyMs, message);
    }

    @Override
    public String failureReason() {
        if (success) {
            return null;
        }
        return message == null || message.isBlank()
                ? "连通性测试未通过（适配器未给出原因）"
                : "连通性测试未通过(adapter=" + adapterType + "): " + message;
    }
}
