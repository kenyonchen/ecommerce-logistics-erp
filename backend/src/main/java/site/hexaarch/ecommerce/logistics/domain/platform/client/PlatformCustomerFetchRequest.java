package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 第三方平台客户（买家）拉取请求参数。
 *
 * @param tenantId  租户ID
 * @param platform  平台名称
 * @param storeUrl  店铺地址
 * @param apiKey    API Key
 * @param apiSecret API Secret
 * @param keyword   买家关键字过滤（可空）
 * @param limit     单页条数
 * @param page      页码，从1开始
 * @author kenyon
 */
public record PlatformCustomerFetchRequest(
        String tenantId,
        String platform,
        String storeUrl,
        String apiKey,
        String apiSecret,
        String keyword,
        Integer limit,
        Integer page
) {
}
