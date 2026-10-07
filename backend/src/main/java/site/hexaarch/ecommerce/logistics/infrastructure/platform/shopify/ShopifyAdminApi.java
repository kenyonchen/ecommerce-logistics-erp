package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shopify Admin REST API 传输层：负责鉴权头、URL 组装、限流退避与 {@code Link} 头游标翻页。
 * <p>
 * 三条来自官方文档的硬约束（https://shopify.dev/docs/api/usage）：
 * <ul>
 *   <li>认证头是 {@code X-Shopify-Access-Token}，不是 {@code X-API-Key}/{@code X-API-Secret}；</li>
 *   <li>REST 分页只能走响应 {@code Link} 头里的 {@code page_info} 游标，带 {@code page} 参数会被拒绝，
 *       因此续页请求必须<b>原样</b>请求 Shopify 返回的那条 URL；</li>
 *   <li>单页 {@code limit} 上限 250。</li>
 * </ul>
 * 接口版本必须显式给出（路径里的 {@code /admin/api/{version}/}）：可在全局配置
 * {@code platform.shopify.api-version} 或集成记录的 {@code apiVersion} 上设置，
 * 后者优先。不内置默认版本号，避免代码里写死一个已过 12 个月支持窗口的版本。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ShopifyAdminApi {

    public static final String ACCESS_TOKEN_HEADER = "X-Shopify-Access-Token";

    /**
     * {@code Link: <url>; rel="next",<url>; rel="previous"}
     */
    private static final Pattern LINK_SECTION = Pattern.compile("<\\s*([^>]+)\\s*>\\s*;\\s*([^,]*)");
    private static final Pattern LINK_PARAMS = Pattern.compile("rel\\s*=\\s*\"?([a-zA-Z]+)\"?");

    private final RestClient restClient;
    private final String configuredApiVersion;
    private final int maxPages;
    private final int retryMaxAttempts;
    private final Duration retryMaxWait;

    public ShopifyAdminApi(RestClient.Builder restClientBuilder,
                           @Qualifier(site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig.PLATFORM_REQUEST_FACTORY)
                           ClientHttpRequestFactory requestFactory,
                           @Value("${platform.shopify.api-version:}") String configuredApiVersion,
                           @Value("${platform.shopify.cursor.max-pages:20}") int maxPages,
                           @Value("${platform.shopify.retry.max-attempts:3}") int retryMaxAttempts,
                           @Value("${platform.shopify.retry.max-wait:30s}") Duration retryMaxWait) {
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.configuredApiVersion = configuredApiVersion == null ? "" : configuredApiVersion.trim();
        this.maxPages = maxPages;
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryMaxWait = retryMaxWait;
    }

    /**
     * 首次请求的绝对 URL：{@code https://{shop}/admin/api/{version}/{resource}?{query}}
     */
    public URI firstRequestUri(PlatformCredential credential, String resource, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(adminBase(credential)).path("/" + resource);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build().toUri();
    }

    /**
     * {@code /admin/api/{version}} 之前的完整前缀，含店铺 origin。
     */
    String adminBase(PlatformCredential credential) {
        String origin = shopOrigin(credential == null ? null : credential.storeUrl());
        return origin + "/admin/api/" + apiVersion(credential);
    }

    /**
     * 单条资源路径（如 {@code orders/123.json}）的绝对 URL。
     */
    public URI resourceUri(PlatformCredential credential, String path) {
        return UriComponentsBuilder.fromUriString(adminBase(credential)).path("/" + path).build().toUri();
    }

    public ResponseEntity<String> get(URI uri, PlatformCredential credential) {
        return execute(HttpMethod.GET, uri, credential, null);
    }

    public ResponseEntity<String> postJson(URI uri, PlatformCredential credential, Object body) {
        return execute(HttpMethod.POST, uri, credential, body);
    }

    /**
     * 从 {@code Link} 头取出 {@code rel="next"} 的 URL；没有则说明已到最后一页。
     */
    public String nextCursorUrl(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null || link.isBlank()) {
            return null;
        }
        Matcher sections = LINK_SECTION.matcher(link);
        while (sections.find()) {
            String url = sections.group(1);
            Matcher params = LINK_PARAMS.matcher(sections.group(2));
            while (params.find()) {
                if ("next".equalsIgnoreCase(params.group(1))) {
                    return url;
                }
            }
        }
        return null;
    }

    public int maxPages() {
        return maxPages;
    }

    /**
     * Shopify 允许的单页上限；请求侧再大也会被压到这里。
     */
    public static int clampPageSize(Integer requested) {
        int size = requested == null || requested <= 0 ? 250 : requested;
        return Math.min(size, 250);
    }

    String apiVersion(PlatformCredential credential) {
        String version = credential == null || credential.apiVersion() == null || credential.apiVersion().isBlank()
                ? configuredApiVersion : credential.apiVersion().trim();
        if (version.isBlank()) {
            throw new BusinessException("Shopify 接口版本未配置：请设置集成记录的 apiVersion，"
                    + "或配置项 platform.shopify.api-version（形如 2026-01，取当前仍在支持窗口内的稳定版本）",
                    "SHOPIFY_API_VERSION_REQUIRED");
        }
        return version;
    }

    private String shopOrigin(String storeUrl) {
        if (storeUrl == null || storeUrl.isBlank()) {
            throw new BusinessException("Shopify 对接需要店铺地址(storeUrl，形如 https://店铺.myshopify.com)",
                    "SHOPIFY_STORE_URL_REQUIRED");
        }
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        URI parsed = URI.create(url);
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new BusinessException("Shopify 店铺地址无法解析主机名: " + storeUrl, "SHOPIFY_STORE_URL_INVALID");
        }
        if (parsed.getHost().equalsIgnoreCase("admin.shopify.com")) {
            throw new BusinessException("Shopify 的 storeUrl 要填 API 域名（https://店铺.myshopify.com），"
                    + "不是后台管理地址 admin.shopify.com", "SHOPIFY_STORE_URL_INVALID");
        }
        return parsed.getScheme() + "://" + parsed.getAuthority();
    }

    /**
     * 外呼执行 + 退避重试。
     * <p>
     * 只对可判定为「暂时性」的失败重试：429（按 {@code Retry-After} 等待）、5xx、网络/超时异常。
     * 401/403 立即失败——令牌错误重试没有意义，只会白耗平台配额并把凭据问题掩盖成慢响应。
     */
    private ResponseEntity<String> execute(HttpMethod httpMethod, URI uri, PlatformCredential credential, Object body) {
        long backoffMs = 1000L;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            boolean canRetry = attempt < retryMaxAttempts;
            try {
                RestClient.RequestBodySpec spec = restClient.method(httpMethod)
                        .uri(uri)
                        .header(ACCESS_TOKEN_HEADER, accessToken(credential))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                if (body != null) {
                    spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                return spec.retrieve().toEntity(String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                lastFailure = e;
                long waitMs = retryAfterMillis(e.getResponseHeaders(), backoffMs);
                if (!canRetry) {
                    break;
                }
                log.warn("[Shopify] 触发限流(429)，{}ms 后第 {}/{} 次重试 uri={}", waitMs, attempt + 1, retryMaxAttempts, uri);
                backoffMs = Math.min(backoffMs * 2, retryMaxWait.toMillis());
                if (!sleep(waitMs)) {
                    throw fetchFailed("Shopify 限流等待被中断", e);
                }
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
                if (!canRetry) {
                    break;
                }
                log.warn("[Shopify] 平台侧异常，{}ms 后第 {}/{} 次重试 uri={} err={}",
                        backoffMs, attempt + 1, retryMaxAttempts, uri, e.getMessage());
                backoffMs = Math.min(backoffMs * 2, retryMaxWait.toMillis());
                if (!sleep(backoffMs)) {
                    throw fetchFailed("Shopify 外呼等待被中断", e);
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw fetchFailed("Shopify 拒绝授权(HTTP " + e.getStatusCode().value() + ")：请检查访问令牌(accessToken)"
                        + "是否属于该店铺、是否具备 Admin orders/products 读写权限", e);
            } catch (RestClientResponseException e) {
                throw fetchFailed("调用 Shopify 失败: HTTP " + e.getStatusCode().value()
                        + (e.getResponseBodyAsString() == null ? "" : " " + truncate(e.getResponseBodyAsString())), e);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastFailure = e instanceof RuntimeException runtimeException ? runtimeException
                        : new IllegalStateException(e);
                throw fetchFailed("调用 Shopify 失败: " + e.getMessage(), e);
            }
        }
        throw fetchFailed("Shopify 连续 " + retryMaxAttempts + " 次调用未成功（限流或平台不可用）", lastFailure);
    }

    private String accessToken(PlatformCredential credential) {
        String token = credential == null ? null : credential.accessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException("Shopify 对接需要访问令牌(accessToken，shpat_ 开头)",
                    "PLATFORM_ACCESS_TOKEN_REQUIRED");
        }
        return token.trim();
    }

    /**
     * 优先用平台给的 {@code Retry-After}；没给则用退避序列，且都不超过 {@code platform.shopify.retry.max-wait}。
     */
    private long retryAfterMillis(HttpHeaders headers, long fallbackMs) {
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        long parsed = 0L;
        if (retryAfter != null) {
            try {
                parsed = Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // HTTP-date 形式的 Retry-After 不常见，退回退避序列
            }
        }
        long capped = parsed > 0 ? parsed : fallbackMs;
        return Math.min(capped, retryMaxWait.toMillis());
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String truncate(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 200) + "...";
    }

    private BusinessException fetchFailed(String message, Throwable cause) {
        if (cause == null) {
            return new BusinessException(message, "SHOPIFY_REQUEST_FAILED");
        }
        BusinessException exception = new BusinessException(message + ": " + cause.getMessage(), "SHOPIFY_REQUEST_FAILED");
        exception.initCause(cause);
        return exception;
    }
}
