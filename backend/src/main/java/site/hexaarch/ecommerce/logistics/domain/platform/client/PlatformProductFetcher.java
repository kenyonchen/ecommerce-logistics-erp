package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.util.Map;

/**
 * 第三方平台商品获取端口：各类平台适配器负责把平台侧商品数据转换为「原始行」。
 *
 * @author kenyon
 */
public interface PlatformProductFetcher {

    /**
     * 适配器注册键：{@code CUSTOM_SHOP}(通用自建店铺协议) / {@code SHOPIFY} / {@code SAMPLE}(示例数据)
     */
    String type();

    /**
     * 拉取平台商品原始行列表
     */
    java.util.List<Map<String, Object>> fetch(PlatformProductFetchRequest request);

    /**
     * 带协议凭据的拉取，见 {@code PlatformOrderFetcher} 的同名方法说明
     */
    default java.util.List<Map<String, Object>> fetch(PlatformProductFetchRequest request, PlatformCredential credential) {
        return fetch(request);
    }
}
