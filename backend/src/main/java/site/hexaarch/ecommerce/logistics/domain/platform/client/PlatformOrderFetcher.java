package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.util.Map;

/**
 * 第三方平台订单获取端口：各类平台适配器负责把平台侧订单数据转换为「原始行」。
 *
 * @author kenyon
 */
public interface PlatformOrderFetcher {

    /**
     * 适配器注册键：{@code CUSTOM_SHOP}(通用自建店铺协议) / {@code SHOPIFY} / {@code SAMPLE}(示例数据)
     */
    String type();

    /**
     * 拉取平台订单原始行列表
     */
    java.util.List<Map<String, Object>> fetch(PlatformOrderFetchRequest request);

    /**
     * 带协议凭据的拉取。真实平台需要访问令牌、接口版本等 {@code Platform*FetchRequest} 装不下的上下文；
     * 通用自建店铺适配器可忽略 {@code credential}，沿用请求里的 storeUrl/apiKey/apiSecret。
     */
    default java.util.List<Map<String, Object>> fetch(PlatformOrderFetchRequest request, PlatformCredential credential) {
        return fetch(request);
    }
}
