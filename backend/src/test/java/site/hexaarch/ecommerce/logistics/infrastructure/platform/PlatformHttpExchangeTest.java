package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外呼传输层的错误语义测试（ISS-131）：连接阶段的失败必须说"地址/连接/超时"，
 * 不能和限流、平台侧故障混成同一句"限流或平台不可用"。
 *
 * @author kenyon
 */
class PlatformHttpExchangeTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 地址解析不了（域名写错是最常见的一种）：不能说成限流，也不能把主机名塞进给用户的话术。
     */
    @Test
    void unresolvableAddressIsReportedAsAddressProblem() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> exchange(1, Duration.ofSeconds(5))
                        .get(java.net.URI.create("http://no-such-host.invalid:9999/orders"), Map.of(), "订单拉取"));
        assertTrue(failure.getMessage().contains("无法解析"), "实际消息: " + failure.getMessage());
        assertFalse(failure.getMessage().contains("限流"), "DNS 失败不该被引向限流: " + failure.getMessage());
        assertEquals("PLATFORM_REQUEST_FAILED", failure.getErrorCode());
        assertNotNull(failure.getCause());
    }

    /**
     * 端口不通 / 服务没起：归"连不上"，同样不许落到"限流"。
     */
    @Test
    void refusedConnectionIsReportedAsUnreachableNotThrottling() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> exchange(1, Duration.ofSeconds(5))
                        .get(java.net.URI.create("http://127.0.0.1:1/orders"), Map.of(), "订单拉取"));
        assertTrue(failure.getMessage().contains("无法连接"), "实际消息: " + failure.getMessage());
        assertFalse(failure.getMessage().contains("限流"), "拒连不该被引向限流: " + failure.getMessage());
    }

    /**
     * 反向守卫：真的是 429 时仍然要说限流，并按平台给的 Retry-After 重试满次数。
     */
    @Test
    void exhaustedRateLimitStillSaysThrottlingAndRetries() {
        startServer(429, "Too Many Requests");
        BusinessException failure = assertThrows(BusinessException.class,
                () -> exchange(2, Duration.ofSeconds(5))
                        .get(java.net.URI.create(baseUrl() + "/orders"), Map.of(), "订单拉取"));
        assertTrue(failure.getMessage().contains("限流"), "实际消息: " + failure.getMessage());
        assertEquals(2, requests.get(), "429 必须重试到 maxAttempts");
    }

    /**
     * 5xx 说"平台侧"并带状态码，不带地址。
     */
    @Test
    void exhaustedServerErrorSaysPlatformSideWithStatus() {
        startServer(503, "Service Unavailable");
        BusinessException failure = assertThrows(BusinessException.class,
                () -> exchange(2, Duration.ofSeconds(5))
                        .get(java.net.URI.create(baseUrl() + "/orders"), Map.of(), "订单拉取"));
        assertTrue(failure.getMessage().contains("平台侧"), "实际消息: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("503"), "应带出状态码: " + failure.getMessage());
        assertEquals(2, requests.get());
    }

    /**
     * 读超时归"超时"族：既不是限流也不是地址错。
     */
    @Test
    void readTimeoutIsReportedAsTimeout() {
        HttpServer slow = startSlowServer();
        BusinessException failure = assertThrows(BusinessException.class, () -> {
            PlatformHttpExchange exchange = new PlatformHttpExchange(RestClient.builder(),
                    requestFactory(Duration.ofMillis(300)), 1, Duration.ofSeconds(5));
            exchange.get(java.net.URI.create("http://127.0.0.1:" + slow.getAddress().getPort() + "/orders"),
                    Map.of(), "订单拉取");
        });
        assertTrue(failure.getMessage().contains("超时"), "实际消息: " + failure.getMessage());
        assertFalse(failure.getMessage().contains("限流"), "超时不该被引向限流: " + failure.getMessage());
    }

    /**
     * 日志侧的根因摘要：连接层异常自身消息以 ": null" 结尾，必须换成 cause 链而不是丢掉。
     */
    @Test
    void causeChainSummaryNeverEndsWithNull() {
        ResourceAccessSamples samples = assertDoesNotThrow(this::collectSamples);
        assertFalse(samples.dnsChain.contains("null"), "实际: " + samples.dnsChain);
        assertFalse(samples.refusedChain.contains("null"), "实际: " + samples.refusedChain);
        assertTrue(samples.dnsChain.contains("UnresolvedAddressException"), "实际: " + samples.dnsChain);
        assertTrue(samples.refusedChain.contains("ClosedChannelException")
                        || samples.refusedChain.contains("ConnectException"),
                "实际: " + samples.refusedChain);
    }

    private record ResourceAccessSamples(String dnsChain, String refusedChain) {
    }

    private ResourceAccessSamples collectSamples() {
        StringBuilder out = new StringBuilder();
        for (String uri : new String[]{"http://no-such-host.invalid:9999/x", "http://127.0.0.1:1/x"}) {
            try {
                exchange(1, Duration.ofSeconds(5)).get(java.net.URI.create(uri), Map.of(), "probe");
                throw new AssertionError("应当抛出: " + uri);
            } catch (BusinessException failure) {
                out.append(PlatformFailures.causeChain(failure.getCause())).append('\n');
            }
        }
        String[] chains = out.toString().split("\n");
        return new ResourceAccessSamples(chains[0], chains[1]);
    }

    private PlatformHttpExchange exchange(int maxAttempts, Duration maxWait) {
        return new PlatformHttpExchange(RestClient.builder(), requestFactory(Duration.ofSeconds(5)),
                maxAttempts, maxWait);
    }

    private static ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        // 显式直连：宿主机开着系统代理时，JDK 默认 ProxySelector 会把 no-such-host.invalid 交给代理，
        // 代理回 502 而非 DNS 失败，本类的"地址解析失败"语义断言会被代理顶掉。
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .proxy(java.net.ProxySelector.of(null))
                        .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private void startServer(int status, String body) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                byte[] payload = body.getBytes();
                exchange.sendResponseHeaders(status, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            });
            server.start();
        } catch (IOException failed) {
            throw new IllegalStateException("本地测试服务启动失败", failed);
        }
    }

    private HttpServer startSlowServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException failed) {
            throw new IllegalStateException("本地测试服务启动失败", failed);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
