package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 连通性测试结果。
 *
 * @param success     凭据与地址是否可用
 * @param adapterType 实际执行测试的适配器注册键
 * @param latencyMs   外呼耗时
 * @param message     失败原因或成功时的店铺标识摘要（不得包含令牌明文）
 * @author kenyon
 */
public record PlatformConnectionTestResult(boolean success, String adapterType, long latencyMs, String message) {

    public static PlatformConnectionTestResult ok(String adapterType, long latencyMs, String message) {
        return new PlatformConnectionTestResult(true, adapterType, latencyMs, message);
    }

    public static PlatformConnectionTestResult failed(String adapterType, long latencyMs, String message) {
        return new PlatformConnectionTestResult(false, adapterType, latencyMs, message);
    }
}
