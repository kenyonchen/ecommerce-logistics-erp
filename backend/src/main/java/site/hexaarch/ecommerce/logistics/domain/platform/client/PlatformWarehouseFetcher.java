package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.util.List;
import java.util.Map;

/**
 * 第三方平台店铺仓库获取端口：各类平台适配器负责把平台侧店铺仓库数据转换为「原始行」。
 * <p>
 * 原始行的字段命名由平台决定，由字段映射服务负责转换为标准店铺仓库字段。
 * </p>
 *
 * @author kenyon
 */
public interface PlatformWarehouseFetcher {

    /**
     * 适配器注册键：{@code CUSTOM_SHOP}(通用自建店铺协议) / {@code SHOPIFY} / {@code SAMPLE}(示例数据)
     */
    String type();

    /**
     * 拉取平台店铺仓库原始行列表
     */
    List<Map<String, Object>> fetch(PlatformWarehouseFetchRequest request);

    /**
     * 带协议凭据的拉取，见 {@code PlatformOrderFetcher} 的同名方法说明
     */
    default List<Map<String, Object>> fetch(PlatformWarehouseFetchRequest request, PlatformCredential credential) {
        return fetch(request);
    }
}
