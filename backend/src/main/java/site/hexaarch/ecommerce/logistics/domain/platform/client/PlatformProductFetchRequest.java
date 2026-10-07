package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 第三方平台商品拉取请求参数。
 *
 * @author kenyon
 */
public record PlatformProductFetchRequest(
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
