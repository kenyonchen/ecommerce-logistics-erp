package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 平台连通性测试端口（BR-04）：在创建/激活集成前用一次最小代价的只读调用验证凭据与地址是否可用。
 * <p>
 * 返回值用结果对象而不是抛异常：测试失败是预期分支，需要把「为什么失败」回显给运维，
 * 而不是让全局异常处理器把它压成 500。
 *
 * @author kenyon
 */
public interface PlatformConnectionTester {

    /**
     * 适配器注册键，取值与 {@code Platform*Fetcher#type()} 同一套（CUSTOM_SHOP / SHOPIFY）
     */
    String type();

    PlatformConnectionTestResult test(PlatformCredential credential);
}
