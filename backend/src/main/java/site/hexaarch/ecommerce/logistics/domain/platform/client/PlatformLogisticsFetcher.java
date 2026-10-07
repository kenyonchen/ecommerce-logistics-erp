package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.util.List;
import java.util.Map;

/**
 * 第三方平台店铺物流数据获取端口：各平台适配器负责把平台侧的发货/运单/轨迹数据转换为「原始行」。
 * <p>
 * 原始行的字段命名由平台决定，由 {@code LogisticsFieldMappingService} 负责转换为标准物流字段。
 * </p>
 *
 * @author kenyon
 */
public interface PlatformLogisticsFetcher {

    /**
     * 适配器注册键：{@code CUSTOM_SHOP}(通用自建店铺协议) / {@code SHOPIFY} / {@code SAMPLE}(示例数据)
     */
    String type();

    /**
     * 拉取平台物流原始行列表
     */
    List<Map<String, Object>> fetch(PlatformLogisticsFetchRequest request);

    /**
     * 带协议凭据的拉取，见 {@code PlatformOrderFetcher} 的同名方法说明
     */
    default List<Map<String, Object>> fetch(PlatformLogisticsFetchRequest request, PlatformCredential credential) {
        return fetch(request);
    }
}
