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
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchException;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetcher;
import site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通用HTTP平台物流数据拉取适配器。
 * <p>
 * 约定：GET {@code {storeUrl}/logistics?page=&pageSize=&orderNumber=&trackingNumber=&shippedFrom=&shippedTo=}，
 * 鉴权通过 {@code X-API-Key} / {@code X-API-Secret} 请求头传递；
 * 响应支持「数组」或「{data|logistics|shipments|orders|items|results|records|list: []}」两种常见结构。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class HttpPlatformLogisticsFetcher implements PlatformLogisticsFetcher {

    private static final String LOGISTICS_PATH = "/logistics";
    private static final List<String> COLLECTION_KEYS =
            List.of("logistics", "shipments", "items", "results", "records", "list");
    private static final DateTimeFormatter PARAM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpPlatformLogisticsFetcher(RestClient.Builder restClientBuilder,
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
    public List<Map<String, Object>> fetch(PlatformLogisticsFetchRequest request) {
        String baseUrl = normalizeUrl(request.storeUrl());
        if (baseUrl == null) {
            throw new PlatformLogisticsFetchException("平台店铺地址(storeUrl)未配置，无法拉取物流数据");
        }

        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.limit() == null || request.limit() <= 0 ? 50 : Math.min(request.limit(), 200);

        try {
            UriComponentsBuilder target = UriComponentsBuilder.fromUriString(baseUrl)
                    .path(LOGISTICS_PATH)
                    .queryParam("page", page)
                    .queryParam("pageSize", pageSize);
            if (hasText(request.orderNumber())) {
                target.queryParam("orderNumber", request.orderNumber().trim());
            }
            if (hasText(request.trackingNumber())) {
                target.queryParam("trackingNumber", request.trackingNumber().trim());
            }
            if (request.shippedFrom() != null) {
                target.queryParam("shippedFrom", request.shippedFrom().format(PARAM_FORMATTER));
            }
            if (request.shippedTo() != null) {
                target.queryParam("shippedTo", request.shippedTo().format(PARAM_FORMATTER));
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
            String message = "调用平台物流接口失败: HTTP " + e.getStatusCode().value() + " " + e.getStatusText();
            log.warn("[PlatformLogisticsFetch] {} platform={} url={}", message, request.platform(), baseUrl);
            throw new PlatformLogisticsFetchException(message, e);
        } catch (PlatformLogisticsFetchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PlatformLogisticsFetch] 拉取平台物流数据异常 platform={} url={}", request.platform(), baseUrl, e);
            throw new PlatformLogisticsFetchException("拉取平台物流数据失败: " + e.getMessage(), e);
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
            throw new PlatformLogisticsFetchException(
                    "平台响应不是合法JSON: " + body.substring(0, Math.min(120, body.length())));
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
            Map<String, Object> row = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
            });
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
