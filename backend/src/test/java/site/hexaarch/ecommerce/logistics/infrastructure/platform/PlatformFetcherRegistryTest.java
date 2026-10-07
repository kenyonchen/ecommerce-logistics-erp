package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 适配器注册表分派测试：确认「按认证协议选适配器」替掉了原来的 {@code useSample ? sample : http} 布尔开关，
 * 且没有适配器的组合是明确报错而不是静默走 mock。
 *
 * @author kenyon
 */
class PlatformFetcherRegistryTest {

    private static final PlatformCredential SHOPIFY_CREDENTIAL = credential(PlatformAuthType.SHOPIFY);
    private static final PlatformCredential CUSTOM_SHOP_CREDENTIAL = credential(PlatformAuthType.CUSTOM_SHOP);

    private final PlatformOrderFetcher shopifyOrder = orderFetcher(PlatformAuthType.SHOPIFY.name());
    private final PlatformOrderFetcher customShopOrder = orderFetcher(PlatformAuthType.CUSTOM_SHOP.name());
    private final PlatformOrderFetcher sampleOrder = orderFetcher(PlatformFetcherRegistry.SAMPLE_TYPE);

    private final PlatformFetcherRegistry registry = new PlatformFetcherRegistry(
            List.of(shopifyOrder, customShopOrder, sampleOrder),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of());

    @Test
    void shouldDispatchByAuthTypeInsteadOfABooleanSwitch() {
        assertSame(shopifyOrder, registry.orderFetcher(SHOPIFY_CREDENTIAL, false));
        assertSame(customShopOrder, registry.orderFetcher(CUSTOM_SHOP_CREDENTIAL, false));
    }

    @Test
    void shouldFallBackToCustomShopWhenAuthTypeMissing() {
        // 存量集成记录没有 auth_type 列值，必须继续走它原来的通用 HTTP 通道
        assertSame(customShopOrder, registry.orderFetcher(credential(null), false));
    }

    @Test
    void shouldPreferSampleAdapterRegardlessOfProtocol() {
        assertSame(sampleOrder, registry.orderFetcher(SHOPIFY_CREDENTIAL, true));
        assertSame(sampleOrder, registry.orderFetcher(CUSTOM_SHOP_CREDENTIAL, true));
    }

    @Test
    void shouldRejectProtocolWithoutAdapterForThatDataLine() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> registry.productFetcher(SHOPIFY_CREDENTIAL, false));

        assertEquals("PLATFORM_ADAPTER_NOT_FOUND", error.getErrorCode());
        assertTrue(error.getMessage().contains("SHOPIFY"), "报错要说明缺的是哪个协议的适配器");
        assertTrue(error.getMessage().contains("商品"), "报错要说明缺的是哪条数据线");
    }

    @Test
    void shouldRejectDuplicateRegistryKeysAtStartup() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new PlatformFetcherRegistry(
                List.of(shopifyOrder, orderFetcher(PlatformAuthType.SHOPIFY.name())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        assertTrue(error.getMessage().contains("SHOPIFY"), "冲突信息要带上重复的注册键");
    }

    @Test
    void shouldDispatchOutboundAdaptersByProtocol() {
        PlatformFulfillmentPusher shopifyPusher = mock(PlatformFulfillmentPusher.class);
        when(shopifyPusher.type()).thenReturn(PlatformAuthType.SHOPIFY.name());
        PlatformConnectionTester customShopTester = mock(PlatformConnectionTester.class);
        when(customShopTester.type()).thenReturn(PlatformAuthType.CUSTOM_SHOP.name());
        PlatformFetcherRegistry outbound = new PlatformFetcherRegistry(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(shopifyPusher), List.of(customShopTester));

        // 出向适配器没有示例数据分支：示例数据没有可探测的对端
        assertSame(shopifyPusher, outbound.fulfillmentPusher(SHOPIFY_CREDENTIAL));
        assertSame(customShopTester, outbound.connectionTester(CUSTOM_SHOP_CREDENTIAL));
        assertThrows(BusinessException.class, () -> outbound.connectionTester(SHOPIFY_CREDENTIAL));
    }

    /**
     * 注册表建索引只用到 {@code type()}，其余方法用不到，因此用 mock 占位。
     */
    private static PlatformOrderFetcher orderFetcher(String type) {
        PlatformOrderFetcher fetcher = mock(PlatformOrderFetcher.class);
        when(fetcher.type()).thenReturn(type);
        return fetcher;
    }

    private static PlatformCredential credential(PlatformAuthType authType) {
        return new PlatformCredential(authType, "tenant-1", "SHOPIFY", "https://demo.myshopify.com",
                "ak", "as", "shpat_token", null, "2026-01", null, null, null, null);
    }

    @Test
    void shouldKeepOtherDataLinesIndependent() {
        PlatformProductFetcher product = mock(PlatformProductFetcher.class);
        when(product.type()).thenReturn(PlatformAuthType.SHOPIFY.name());
        PlatformFetcherRegistry withProduct = new PlatformFetcherRegistry(
                List.of(shopifyOrder), List.of(product), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        assertSame(product, withProduct.productFetcher(SHOPIFY_CREDENTIAL, false));
        assertSame(shopifyOrder, withProduct.orderFetcher(SHOPIFY_CREDENTIAL, false));
    }
}
