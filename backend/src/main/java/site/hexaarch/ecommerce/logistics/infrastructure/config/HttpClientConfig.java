package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 第三方平台外呼的 HTTP 客户端配置。
 * <p>
 * JDK HttpClient 默认不设超时，平台侧无响应会一直挂住导入线程并占住数据库连接。
 * </p>
 *
 * @author kenyon
 */
@Configuration
public class HttpClientConfig {

    public static final String PLATFORM_REQUEST_FACTORY = "platformRequestFactory";

    @Bean(name = PLATFORM_REQUEST_FACTORY)
    public ClientHttpRequestFactory platformRequestFactory(
            @Value("${platform.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${platform.http.read-timeout:15s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
