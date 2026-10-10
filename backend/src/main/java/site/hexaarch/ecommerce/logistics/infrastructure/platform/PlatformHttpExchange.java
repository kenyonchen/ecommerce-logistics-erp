package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 第三方平台外呼的共用传输层：超时、限流退避、错误语义化。
 * <p>
 * 九家平台的传输约束高度同构（429 要按 {@code Retry-After} 等、401/403 重试没有意义、
 * 5xx 与网络抖动值得重试），所以收敛到一个组件，各适配器只负责拼 URL 与鉴权头/签名。
 * Shopify 是唯一例外：它的分页只认 {@code Link} 头游标、且已有契约测试覆盖，
 * 保留自己的 {@code ShopifyAdminApi} 而不并入这里，避免为了统一去动一条已验证的链路。
 * <p>
 * 任何方法都不会把请求头写进日志或异常消息——凭据只可能出现在头里。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class PlatformHttpExchange {

    private final RestClient restClient;
    private final int maxAttempts;
    private final Duration maxWait;

    public PlatformHttpExchange(RestClient.Builder restClientBuilder,
                                @Qualifier(site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig
                                        .PLATFORM_REQUEST_FACTORY)
                                ClientHttpRequestFactory requestFactory,
                                @Value("${platform.http.retry.max-attempts:3}") int maxAttempts,
                                @Value("${platform.http.retry.max-wait:30s}") Duration maxWait) {
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.maxWait = maxWait;
    }

    public ResponseEntity<String> get(URI uri, Map<String, String> headers, String label) {
        return execute(HttpMethod.GET, uri, headers, null, null, label);
    }

    public ResponseEntity<String> postJson(URI uri, Map<String, String> headers, String json, String label) {
        return execute(HttpMethod.POST, uri, headers, json, null, label);
    }

    public ResponseEntity<String> putJson(URI uri, Map<String, String> headers, String json, String label) {
        return execute(HttpMethod.PUT, uri, headers, json, null, label);
    }

    /**
     * 表单向接口（TOP / 令牌端点一类）：{@code application/x-www-form-urlencoded}。
     */
    public ResponseEntity<String> postForm(URI uri, Map<String, String> headers, Map<String, String> form, String label) {
        return execute(HttpMethod.POST, uri, headers, null, form, label);
    }

    private ResponseEntity<String> execute(HttpMethod method, URI uri, Map<String, String> headers,
                                           String json, Map<String, String> form, String label) {
        long backoffMs = 1000L;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean canRetry = attempt < maxAttempts;
            try {
                RestClient.RequestBodySpec spec = restClient.method(method).uri(uri).accept(MediaType.APPLICATION_JSON);
                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        if (header.getValue() != null) {
                            spec = spec.header(header.getKey(), header.getValue());
                        }
                    }
                }
                if (json != null) {
                    spec = spec.contentType(MediaType.APPLICATION_JSON).body(json);
                } else if (form != null) {
                    MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
                    form.forEach((key, value) -> values.add(key, value == null ? "" : value));
                    spec = spec.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(values);
                }
                return spec.retrieve().toEntity(String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                lastFailure = e;
                long waitMs = Math.min(retryAfterMillis(e.getResponseHeaders(), backoffMs), maxWait.toMillis());
                if (!canRetry) {
                    break;
                }
                log.warn("[{}] 触发限流(429)，{}ms 后第 {}/{} 次重试 uri={}", label, waitMs, attempt + 1, maxAttempts, uri);
                backoffMs = Math.min(backoffMs * 2, maxWait.toMillis());
                if (!sleep(waitMs)) {
                    throw failed(label + " 限流等待被中断", e);
                }
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
                if (!canRetry) {
                    break;
                }
                log.warn("[{}] 平台侧异常，{}ms 后第 {}/{} 次重试 uri={} err={}",
                        label, backoffMs, attempt + 1, maxAttempts, uri, PlatformFailures.causeChain(e));
                backoffMs = Math.min(backoffMs * 2, maxWait.toMillis());
                if (!sleep(backoffMs)) {
                    throw failed(label + " 外呼等待被中断", e);
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw failed(label + " 拒绝授权(HTTP " + e.getStatusCode().value()
                        + "): " + briefBody(e) + "；请检查凭据、令牌是否过期以及该店铺/区域是否匹配", e);
            } catch (RestClientResponseException e) {
                throw failed(label + " 调用失败: HTTP " + e.getStatusCode().value() + " " + briefBody(e), e);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastFailure = e instanceof RuntimeException runtimeException ? runtimeException
                        : new IllegalStateException(e);
                throw failed(label + " 调用失败: " + PlatformFailures.userReason(e), e);
            }
        }
        throw failed(PlatformFailures.exhausted(label, maxAttempts, lastFailure), lastFailure);
    }

    /**
     * 优先用平台给的 {@code Retry-After}；没给则用退避序列。
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
        return parsed > 0 ? parsed : fallbackMs;
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

    /**
     * 平台错误体常有几百字节，截断后再拼进异常消息；只含响应体，不含请求头。
     */
    private static String briefBody(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return "";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 300 ? collapsed : collapsed.substring(0, 300) + "...";
    }

    private static BusinessException failed(String message, Throwable cause) {
        BusinessException exception = new BusinessException(message, "PLATFORM_REQUEST_FAILED");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
