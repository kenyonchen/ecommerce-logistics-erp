package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.Map;

/**
 * Shopify 连通性测试：一次最小代价的只读调用 {@code GET /admin/api/{version}/shop.json}。
 * <p>
 * 选 {@code shop.json} 是因为它对权限要求最低（任何有效访问令牌都能调通），却能同时验证
 * 令牌、店铺域名、接口版本三件事。失败按结果返回而不是抛异常，让前端能把原因显示给运维（BR-04）。
 * 回显信息只含店铺标识，不含令牌。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopifyConnectionTester implements PlatformConnectionTester {

    private static final String SHOP_RESOURCE = "shop.json";

    private final ShopifyAdminApi api;

    public ShopifyConnectionTester(ShopifyAdminApi api) {
        this.api = api;
    }

    @Override
    public String type() {
        return PlatformAuthType.SHOPIFY.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<String> response = api.get(api.resourceUri(credential, SHOP_RESOURCE), credential);
            Map<String, Object> shop = ShopifyJson.wrappedObject(response.getBody(), "shop");
            String domain = firstNonBlank(ShopifyJson.text(shop, "myshopify_domain"),
                    credential == null ? null : credential.storeUrl());
            String summary = "HTTP " + response.getStatusCode().value() + " 已连接店铺 "
                    + firstNonBlank(ShopifyJson.text(shop, "name"), domain, "(未返回名称)")
                    + "，接口版本 " + api.apiVersion(credential)
                    + "，币种 " + firstNonBlank(ShopifyJson.text(shop, "currency"), "未知");
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt), summary);
        } catch (Exception e) {
            String reason = firstNonBlank(e.getMessage(), e.getClass().getSimpleName());
            log.warn("[Shopify] 连通性测试失败 store={} err={}",
                    credential == null ? null : credential.storeUrl(), reason);
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt), reason);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
