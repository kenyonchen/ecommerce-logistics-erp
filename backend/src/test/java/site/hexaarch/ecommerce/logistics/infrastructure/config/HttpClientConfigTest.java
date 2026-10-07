package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.HttpPlatformOrderFetcher;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台外呼超时配置测试：平台无响应时必须快速失败，不能无限挂起。
 *
 * @author kenyon
 */
class HttpClientConfigTest {

    private final HttpClientConfig httpClientConfig = new HttpClientConfig();
    private final PlatformOrderFetchRequest request = new PlatformOrderFetchRequest(
            "default-tenant", "AMAZON", null, "key", "secret", null, null, null, 10, 1);

    @Test
    void hangingPlatformResponseShouldFailWithinReadTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            holdConnectionOpen(server);
            String url = "http://127.0.0.1:" + server.getLocalPort();

            HttpPlatformOrderFetcher fetcher = new HttpPlatformOrderFetcher(RestClient.builder(),
                    httpClientConfig.platformRequestFactory(Duration.ofSeconds(2), Duration.ofMillis(600)));

            long startedAt = System.nanoTime();
            PlatformOrderFetchException ex = assertThrows(PlatformOrderFetchException.class,
                    () -> fetcher.fetch(withUrl(request, url)));
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            assertTrue(elapsedMillis < 5_000,
                    "外呼未在读超时内中止（耗时 " + elapsedMillis + "ms）: " + ex.getMessage());
        }
    }

    @Test
    void unreachablePlatformShouldFailWithinConnectTimeout() {
        // 127.0.0.1:1 通常立即拒绝连接；关键是不得无限等待
        HttpPlatformOrderFetcher fetcher = new HttpPlatformOrderFetcher(RestClient.builder(),
                httpClientConfig.platformRequestFactory(Duration.ofMillis(500), Duration.ofMillis(500)));

        assertThrows(PlatformOrderFetchException.class,
                () -> fetcher.fetch(withUrl(request, "http://127.0.0.1:1")));
    }

    /**
     * 接受连接、读走请求字节但永不返回响应，模拟平台侧挂起
     */
    private void holdConnectionOpen(ServerSocket server) {
        Thread blocker = new Thread(() -> {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[1024];
                while (in.read(buffer) != -1) {
                    // 只消费、不应答
                }
            } catch (IOException ignored) {
                // 客户端超时断开属预期
            }
        });
        blocker.setDaemon(true);
        blocker.start();
    }

    private PlatformOrderFetchRequest withUrl(PlatformOrderFetchRequest source, String storeUrl) {
        return new PlatformOrderFetchRequest(source.tenantId(), source.platform(), storeUrl,
                source.apiKey(), source.apiSecret(), source.orderNumber(),
                source.updatedFrom(), source.updatedTo(), source.limit(), source.page());
    }
}
