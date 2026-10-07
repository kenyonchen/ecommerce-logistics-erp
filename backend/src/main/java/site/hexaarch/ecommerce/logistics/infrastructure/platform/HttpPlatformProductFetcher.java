package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.ClientHttpRequestFactory;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchException;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;
import site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通用HTTP商品拉取适配器。
 * <p>
 * 约定：GET {@code {storeUrl}/products?page=&pageSize=&keyword=}，
 * 鉴权通过 {@code X-API-Key} / {@code X-API-Secret} 请求头传递；
 * 响应支持「数组」或「{data|products|items|results: []}」两种常见结构。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class HttpPlatformProductFetcher implements PlatformProductFetcher {

    private static final String PRODUCTS_PATH = "/products";
    private static final List<String> COLLECTION_KEYS = List.of("products", "items", "results", "records", "list");

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpPlatformProductFetcher(RestClient.Builder restClientBuilder,
                                 @Qualifier(HttpClientConfig.PLATFORM_REQUEST_FACTORY)
                                 ClientHttpRequestFactory requestFactory) {
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String type() {
        return PlatformAuthType.CUSTOM_SHOP.name();
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        String baseUrl = normalizeUrl(request.storeUrl());
        if (baseUrl == null) {
            throw new PlatformProductFetchException("平台店铺地址(storeUrl)未配置，无法拉取商品");
        }

        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.limit() == null || request.limit() <= 0 ? 50 : Math.min(request.limit(), 200);

        try {
            UriComponentsBuilder target = UriComponentsBuilder.fromUriString(baseUrl)
                    .path(PRODUCTS_PATH)
                    .queryParam("page", page)
                    .queryParam("pageSize", pageSize);
            if (request.keyword() != null && !request.keyword().isBlank()) {
                target.queryParam("keyword", request.keyword().trim());
            }
            String url = target.encode().toUriString();

            String body = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-API-Key", request.apiKey() == null ? "" : request.apiKey())
                    .header("X-API-Secret", request.apiSecret() == null ? "" : request.apiSecret())
                    .retrieve()
                    .body(String.class);
            return parseBody(body);
        } catch (RestClientResponseException e) {
            String message = "调用平台接口失败: HTTP " + e.getStatusCode().value() + " " + e.getStatusText();
            log.warn("[PlatformFetch] {} platform={} url={}", message, request.platform(), baseUrl);
            throw new PlatformProductFetchException(message, e);
        } catch (PlatformProductFetchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PlatformFetch] 拉取平台商品异常 platform={} url={}", request.platform(), baseUrl, e);
            throw new PlatformProductFetchException("拉取平台商品失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析平台响应体，兼容数组与包装对象两种结构
     */
    List<Map<String, Object>> parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }
        Object parsed;
        try {
            parsed = objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            throw new PlatformProductFetchException("平台响应不是合法JSON: " + body.substring(0, Math.min(120, body.length())));
        }
        Object collection = parsed;
        if (parsed instanceof Map<?, ?> map) {
            Object data = map.get("data");
            Object candidate = data != null ? unwrap(data) : null;
            if (candidate == null) {
                for (String key : COLLECTION_KEYS) {
                    if (map.get(key) != null) {
                        candidate = unwrap(map.get(key));
                        break;
                    }
                }
            }
            collection = candidate;
        }
        if (!(collection instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(toObjectMap(map));
            } else {
                Map<String, Object> single = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                });
                if (single != null) {
                    rows.add(single);
                }
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Object unwrap(Object value) {
        if (value instanceof List<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : COLLECTION_KEYS) {
                if (map.get(key) instanceof List<?>) {
                    return map.get(key);
                }
            }
        }
        return value;
    }

    private Map<String, Object> toObjectMap(Map<?, ?> map) {
        return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {
        });
    }

    private String normalizeUrl(String storeUrl) {
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
        return url;
    }
}
