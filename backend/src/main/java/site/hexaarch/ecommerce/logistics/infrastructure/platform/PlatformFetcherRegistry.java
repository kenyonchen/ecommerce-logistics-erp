package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFinanceFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 平台适配器注册表：按 {@link PlatformAuthType}（而非布尔开关）选择数据线适配器。
 * <p>
 * 分派键就是各适配器 {@code type()} 的返回值：{@code CUSTOM_SHOP} 走通用自建店铺 HTTP 协议，
 * {@code SHOPIFY} 走 Shopify Admin API，{@value #SAMPLE_TYPE} 是示例数据适配器。
 * 新增一家平台只需再注册一个 {@code type()} 返回该协议名的适配器，不必改动导入服务。
 *
 * @author kenyon
 */
@Component
public class PlatformFetcherRegistry {

    /**
     * 示例数据适配器的注册键
     */
    public static final String SAMPLE_TYPE = "SAMPLE";

    private final Map<String, PlatformOrderFetcher> orderFetchers;
    private final Map<String, PlatformProductFetcher> productFetchers;
    private final Map<String, PlatformCustomerFetcher> customerFetchers;
    private final Map<String, PlatformLogisticsFetcher> logisticsFetchers;
    private final Map<String, PlatformWarehouseFetcher> warehouseFetchers;
    private final Map<String, PlatformFinanceFetcher> financeFetchers;
    private final Map<String, PlatformFulfillmentPusher> fulfillmentPushers;
    private final Map<String, PlatformConnectionTester> connectionTesters;

    public PlatformFetcherRegistry(List<PlatformOrderFetcher> orderFetchers,
                                  List<PlatformProductFetcher> productFetchers,
                                  List<PlatformCustomerFetcher> customerFetchers,
                                  List<PlatformLogisticsFetcher> logisticsFetchers,
                                  List<PlatformWarehouseFetcher> warehouseFetchers,
                                  List<PlatformFinanceFetcher> financeFetchers,
                                  List<PlatformFulfillmentPusher> fulfillmentPushers,
                                  List<PlatformConnectionTester> connectionTesters) {
        this.orderFetchers = index(orderFetchers, PlatformOrderFetcher::type, "订单");
        this.productFetchers = index(productFetchers, PlatformProductFetcher::type, "商品");
        this.customerFetchers = index(customerFetchers, PlatformCustomerFetcher::type, "客户");
        this.logisticsFetchers = index(logisticsFetchers, PlatformLogisticsFetcher::type, "物流");
        this.warehouseFetchers = index(warehouseFetchers, PlatformWarehouseFetcher::type, "店铺仓库");
        this.financeFetchers = index(financeFetchers, PlatformFinanceFetcher::type, "财务");
        this.fulfillmentPushers = index(fulfillmentPushers, PlatformFulfillmentPusher::type, "发货回写");
        this.connectionTesters = index(connectionTesters, PlatformConnectionTester::type, "连通性测试");
    }

    public PlatformOrderFetcher orderFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(orderFetchers, credential, useSampleData, "订单");
    }

    public PlatformProductFetcher productFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(productFetchers, credential, useSampleData, "商品");
    }

    public PlatformCustomerFetcher customerFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(customerFetchers, credential, useSampleData, "客户");
    }

    public PlatformLogisticsFetcher logisticsFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(logisticsFetchers, credential, useSampleData, "物流");
    }

    public PlatformWarehouseFetcher warehouseFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(warehouseFetchers, credential, useSampleData, "店铺仓库");
    }

    public PlatformFinanceFetcher financeFetcher(PlatformCredential credential, boolean useSampleData) {
        return resolve(financeFetchers, credential, useSampleData, "财务");
    }

    /**
     * 发货回写适配器：反向写数据，没有「示例数据」分支，只按认证协议分派。
     */
    public PlatformFulfillmentPusher fulfillmentPusher(PlatformCredential credential) {
        return resolve(fulfillmentPushers, credential, false, "发货回写");
    }

    /**
     * 连通性测试适配器：同上，按认证协议分派。勾选了「使用示例数据」的集成仍会真实探测
     * 已配置的店铺地址——测的就是凭据通不通，示例数据没有可探测的对端。
     */
    public PlatformConnectionTester connectionTester(PlatformCredential credential) {
        return resolve(connectionTesters, credential, false, "连通性测试");
    }

    /**
     * 示例数据键优先于协议：勾选「使用示例数据」时无论什么平台都走 mock，保持与原实现一致的语义。
     */
    private static String registryKey(PlatformAuthType authType, boolean useSampleData) {
        if (useSampleData) {
            return SAMPLE_TYPE;
        }
        return authType == null ? PlatformAuthType.CUSTOM_SHOP.name() : authType.name();
    }

    private static <T> Map<String, T> index(List<T> adapters, Function<T, String> keyFn, String line) {
        Map<String, T> byType = new HashMap<>();
        for (T adapter : adapters) {
            String key = keyFn.apply(adapter);
            T previous = byType.put(key, adapter);
            if (previous != null) {
                throw new IllegalStateException("平台" + line + "适配器注册键冲突: " + key
                        + "，已有 " + previous.getClass().getSimpleName() + "，又出现 " + adapter.getClass().getSimpleName());
            }
        }
        return Map.copyOf(byType);
    }

    private static <T> T resolve(Map<String, T> byType, PlatformCredential credential,
                                 boolean useSampleData, String line) {
        PlatformAuthType authType = credential == null ? null : credential.authType();
        String key = registryKey(authType, useSampleData);
        T adapter = byType.get(key);
        if (adapter == null) {
            throw new BusinessException("平台" + line + "数据线没有 " + key + " 适配器（已注册: "
                    + new java.util.TreeSet<>(byType.keySet()) + "）；可改用 CUSTOM_SHOP 协议或文件上传导入",
                    "PLATFORM_ADAPTER_NOT_FOUND");
        }
        return adapter;
    }
}
