package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTester;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig;

import java.net.URI;

/**
 * 自建店铺连通性测试：按通用 HTTP 约定探测 {@code GET {storeUrl}/orders?page=1&pageSize=1}，
 * 凭据走 {@code X-API-Key} / {@code X-API-Secret} 请求头。
 * <p>
 * 没有单独的 health 端点可依赖，所以取约定里代价最小的一条真实业务查询：pageSize=1 既能证明
 * 地址与鉴权可用，又不会把对方库拉一遍。返回 2xx 即视为通过。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class HttpPlatformConnectionTester implements PlatformConnectionTester {

    private static final String ORDERS_PATH = "/orders";

    private final RestClient restClient;

    public HttpPlatformConnectionTester(RestClient.Builder restClientBuilder,
                                        @Qualifier(HttpClientConfig.PLATFORM_REQUEST_FACTORY)
                                        ClientHttpRequestFactory requestFactory) {
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public String type() {
        return PlatformAuthType.CUSTOM_SHOP.name();
    }

    @Override
    public PlatformConnectionTestResult test(PlatformCredential credential) {
        String baseUrl = normalizeUrl(credential == null ? null : credential.storeUrl());
        if (baseUrl == null) {
            return PlatformConnectionTestResult.failed(type(), 0L,
                    "店铺地址(storeUrl)未配置，无法测试连通性");
        }
        long startedAt = System.nanoTime();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path(ORDERS_PATH)
                .queryParam("page", 1).queryParam("pageSize", 1).encode().build().toUri();
        try {
            ResponseEntity<String> response = restClient.get().uri(uri)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-API-Key", credential.apiKey() == null ? "" : credential.apiKey())
                    .header("X-API-Secret", credential.apiSecret() == null ? "" : credential.apiSecret())
                    .retrieve().toEntity(String.class);
            return PlatformConnectionTestResult.ok(type(), elapsedMs(startedAt),
                    "HTTP " + response.getStatusCode().value() + " 店铺地址与密钥可用");
        } catch (RestClientResponseException e) {
            log.warn("[PlatformConnectionTest] 自建店铺探测失败 url={} status={}", baseUrl, e.getStatusCode().value());
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt),
                    "HTTP " + e.getStatusCode().value() + " " + e.getStatusText()
                            + "：请检查店铺地址与 apiKey/apiSecret");
        } catch (Exception e) {
            log.warn("[PlatformConnectionTest] 自建店铺探测异常 url={} err={}",
                    baseUrl, PlatformFailures.causeChain(e));
            // 探活失败是结果不是异常：按族给一句话（地址/连接/超时），不要把包装层的 ": null" 尾巴回显给用户
            return PlatformConnectionTestResult.failed(type(), elapsedMs(startedAt),
                    PlatformFailures.userReason(e));
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static String normalizeUrl(String storeUrl) {
        if (storeUrl == null || storeUrl.isBlank()) {
            return null;
        }
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            if (UriComponentsBuilder.fromUriString(url).build().toUri().getHost() == null) {
                throw new BusinessException("店铺地址无法解析主机名: " + storeUrl, "PLATFORM_STORE_URL_INVALID");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("店铺地址格式不正确: " + storeUrl, "PLATFORM_STORE_URL_INVALID");
        }
        return url;
    }
}
