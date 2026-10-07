package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 第三方平台店铺仓库拉取请求参数。
 *
 * @param tenantId  租户ID
 * @param platform  平台名称
 * @param storeUrl  平台店铺地址
 * @param apiKey    平台API Key
 * @param apiSecret 平台API Secret
 * @param keyword   检索关键字（店铺/仓库）
 * @param limit     拉取条数上限
 * @param page      页码（从1开始）
 * @author kenyon
 */
public record PlatformWarehouseFetchRequest(
        String tenantId,
        String platform,
        String storeUrl,
        String apiKey,
        String apiSecret,
        String keyword,
        Integer limit,
        Integer page) {
}
