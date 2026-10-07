package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台物流HTTP适配器的响应解析测试。
 *
 * @author kenyon
 */
class HttpPlatformLogisticsFetcherTest {

    private final HttpPlatformLogisticsFetcher fetcher = new HttpPlatformLogisticsFetcher(
            RestClient.builder(), requestFactory());

    private static ClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Test
    void shouldParseJsonArrayResponse() {
        List<Map<String, Object>> rows = fetcher.parseBody(
                "[{\"shipmentId\":\"S-1\",\"trackingNumber\":\"TN-1\"},{\"shipmentId\":\"S-2\"}]");
        assertEquals(2, rows.size());
        assertEquals("TN-1", rows.get(0).get("trackingNumber"));
    }

    @Test
    void shouldUnwrapDataAndCollectionKeyResponses() {
        List<Map<String, Object>> byData = fetcher.parseBody(
                "{\"data\":{\"shipments\":[{\"shipmentId\":\"S-1\"}]}}");
        assertEquals(1, byData.size());
        assertEquals("S-1", byData.get(0).get("shipmentId"));

        List<Map<String, Object>> byKey = fetcher.parseBody(
                "{\"logistics\":[{\"shipmentId\":\"S-2\"},{\"shipmentId\":\"S-3\"}]}");
        assertEquals(2, byKey.size());
    }

    @Test
    void shouldReturnEmptyForNonListPayload() {
        assertTrue(fetcher.parseBody("{\"message\":\"no data\"}").isEmpty());
        assertTrue(fetcher.parseBody("").isEmpty());
    }

    @Test
    void shouldRejectInvalidJsonAndMissingStoreUrl() {
        assertThrows(PlatformLogisticsFetchException.class, () -> fetcher.parseBody("not-a-json"));
        assertThrows(PlatformLogisticsFetchException.class, () -> fetcher.fetch(
                new site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchRequest(
                        "default-tenant", "AMAZON", " ", null, null, null, null, null, null, 10, 1)));
    }
}
