package site.hexaarch.ecommerce.logistics.infrastructure.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * API 请求/响应日志的配置项。
 * <p>
 * 前缀 {@code app.api-logging}，全部有默认值，不配置也能直接用。
 * 生产环境建议 {@code enabled: false}，或至少关掉 headers（ Authorization / Cookie 属于敏感信息）。
 * </p>
 *
 * @author kenyon
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.api-logging")
public class ApiLoggingProperties {

    /**
     * 是否开启请求/响应日志
     */
    private boolean enabled = true;

    /**
     * 是否打印请求头（Authorization、Cookie 等敏感头会被打码）
     */
    private boolean includeHeaders = true;

    /**
     * 请求体/响应体打印的最大字符数，超出部分截断
     */
    private int maxBodyLength = 4096;

    /**
     * 不打印日志的路径（Ant 风格）
     */
    private List<String> excludePatterns = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/favicon.ico",
            "/error",
            "/css/**",
            "/js/**",
            "/images/**"
    );
}
