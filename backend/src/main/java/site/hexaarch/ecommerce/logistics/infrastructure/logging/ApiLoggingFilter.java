package site.hexaarch.ecommerce.logistics.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 请求/响应全量日志过滤器。
 * <p>
 * 打印内容：方法、URI、QueryString、客户端 IP、请求头、请求体、响应状态码、响应体、耗时、当前登录用户。
 * 格式为一次请求一个 traceId 贯穿：
 * </p>
 * <pre>
 * [a1b2c3d4] --&gt; POST /api/auth/login?x=1 from=127.0.0.1
 * [a1b2c3d4]    headers: authorization=******, content-type=application/json
 * [a1b2c3d4]    body: {"username":"admin","password":"******"}
 * [a1b2c3d4] &lt;-- 200 (57ms) user=admin
 * [a1b2c3d4]    response: {"code":"200","message":"success","data":{...}}
 * </pre>
 * <p>
 * 安全与性能取舍：
 * 1. Authorization/Cookie 等请求头，以及 password/token/secret/apiKey 等 JSON 字段统一打码；
 * 2. multipart 文件上传、二进制响应（Excel 导出等）只打印元信息，不打印内容，也不做内存缓存；
 * 3. 响应体走 {@link ContentCachingResponseWrapper}，日志写完后必须 copyBodyToResponse，否则客户端收不到数据。
 * </p>
 * <p>
 * 日志级别为 DEBUG，需在配置里把本类 logger 设为 DEBUG 才会输出（见 application.yml）。
 * </p>
 *
 * @author kenyon
 */
@Component
@Order(ApiLoggingFilter.ORDER)
public class ApiLoggingFilter extends OncePerRequestFilter {

    /**
     * 早于 Spring Security 过滤器链（默认 -100），这样被安全层拦下的 401/403 响应也能记录下来。
     */
    public static final int ORDER = -200;

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

    private static final String TRACE_ID_KEY = "traceId";
    private static final String MASK = "******";
    private static final String EMPTY_BODY = "<空>";
    private static final String NOT_CACHED = "<未缓存>";

    /**
     * 头部打码名单（小写比较）
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");

    /**
     * 命中即打码的 JSON 字段名：password / token / secret / apiKey / accessKey / credential 等
     */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "\"([^\"]*(?:password|passwd|token|secret|api[_-]?key|access[_-]?key|credential)[^\"]*)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private final ApiLoggingProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiLoggingFilter(ApiLoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled() || isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);
        long startNanos = System.nanoTime();

        String requestContentType = request.getContentType();
        boolean cacheRequestBody = isPrintable(requestContentType) && !isMultipart(requestContentType);
        HttpServletRequest requestToUse = cacheRequestBody ? new ContentCachingRequestWrapper(request) : request;
        ContentCachingResponseWrapper responseToUse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            // 请求行、请求头、请求体、响应状态、响应体合并成一次日志打印（请求体须等下游读取后才有内容）
            logCombined(traceId, request, requestToUse, cacheRequestBody, responseToUse,
                    (System.nanoTime() - startNanos) / 1_000_000);
            // 缓存的响应体必须写回原始响应，否则客户端拿不到任何数据
            responseToUse.copyBodyToResponse();
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 一次日志里打出完整请求/响应：请求行 + 请求头 + 请求体 + 响应状态（耗时/当前用户）+ 响应体。
     */
    private void logCombined(String traceId, HttpServletRequest request, HttpServletRequest bodyRequest,
                             boolean cacheRequestBody, ContentCachingResponseWrapper response, long elapsedMillis) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("\n=====================request start======================");
        sb.append("\n").append(request.getMethod()).append(" ").append(request.getRequestURI());
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isBlank()) {
            sb.append('?').append(queryString);
        }
        sb.append(" from=").append(clientIp(request));
        if (properties.isIncludeHeaders()) {
            sb.append("\nheaders: ").append(headersOf(request));
        }
        String body = requestBody(bodyRequest, cacheRequestBody);
        if (!EMPTY_BODY.equals(body)) {
            sb.append("\nbody: ").append(body);   
        }
        sb.append("\n==============request end & response start==============");
        String username = currentUsername();
        if (username != null && !username.isBlank()) {
            sb.append(" user=").append(username);
        }
        sb.append("\nresponse: ").append(responseBody(response));
        sb.append("\n").append(response.getStatus()).append(" (").append(elapsedMillis).append("ms)");
        sb.append("\n======================response end=======================");
        log.debug(sb.toString());
    }

    private String requestBody(HttpServletRequest request, boolean cacheRequestBody) {
        String contentType = request.getContentType();
        if (isMultipart(contentType)) {
            // 上传文件（最大 20MB）不缓存、不打印内容，只给出参数名
            return "<multipart/form-data，文件内容不打印> fields=" + request.getParameterMap().keySet();
        }
        if (!isPrintable(contentType)) {
            return "<" + contentType + "，二进制内容不打印>";
        }
        if (!cacheRequestBody) {
            return NOT_CACHED;
        }
        byte[] content = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
        String body = new String(content, charsetOf(request));
        if (body.isBlank()) {
            // form 表单提交时 body 已被参数解析消费掉，退回打印参数
            Map<String, String[]> params = request.getParameterMap();
            return params.isEmpty() ? EMPTY_BODY : "form=" + paramsOf(params);
        }
        return truncate(maskSensitive(body));
    }

    private String responseBody(ContentCachingResponseWrapper response) {
        String contentType = response.getContentType();
        if (!isPrintable(contentType)) {
            return "<" + (contentType == null ? "无" : contentType) + "，共 " + response.getContentSize()
                    + " 字节，二进制内容不打印>";
        }
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return EMPTY_BODY;
        }
        return truncate(maskSensitive(new String(content, charsetOf(response))));
    }

    private String headersOf(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String value = SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? MASK : request.getHeader(name);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name).append('=').append(value);
        }
        return sb.length() == 0 ? EMPTY_BODY : sb.toString();
    }

    private String paramsOf(Map<String, String[]> params) {
        StringBuilder sb = new StringBuilder("{");
        params.forEach((name, values) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            String value = (values == null || values.length == 0) ? "" : values[0];
            // 表单里也可能带 token/密码
            sb.append(name).append('=').append(
                    SENSITIVE_FIELD_PATTERN.matcher("\"" + name + "\":\"" + value + "\"").find() ? MASK : value);
        });
        return sb.append('}').toString();
    }

    /**
     * 把 password / token / secret 等字段的值替换成 ******
     */
    private String maskSensitive(String body) {
        Matcher matcher = SENSITIVE_FIELD_PATTERN.matcher(body);
        StringBuilder sb = new StringBuilder(body.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, "\"" + matcher.group(1) + "\":\"" + MASK + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String truncate(String text) {
        int max = properties.getMaxBodyLength() > 0 ? properties.getMaxBodyLength() : Integer.MAX_VALUE;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(已截断，总长度 " + text.length() + ")";
    }

    private boolean isExcluded(HttpServletRequest request) {
        List<String> patterns = properties.getExcludePatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        String uri = request.getRequestURI();
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    /**
     * 只打印文本内容，图片/Excel/PDF 等二进制一律跳过
     */
    private boolean isPrintable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return type.startsWith("text/")
                || type.startsWith("application/json")
                || type.startsWith("application/xml")
                || type.startsWith("application/x-ndjson")
                || type.contains("x-www-form-urlencoded")
                || type.endsWith("+json")
                || type.endsWith("+xml");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    /**
     * 从 Content-Type 解析字符集。Servlet 的 {@code getCharacterEncoding()} 对 JSON 不可靠
     * （Jackson 写 UTF-8 字节却不一定设置响应字符集，常返回默认的 ISO-8859-1），
     * 直接用它会把中文打成乱码，因此缺省回退 UTF-8。
     */
    private Charset charsetOf(HttpServletRequest request) {
        return charsetFromContentType(request.getContentType());
    }

    private Charset charsetOf(HttpServletResponse response) {
        return charsetFromContentType(response.getContentType());
    }

    private Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            String[] parts = contentType.toLowerCase(Locale.ROOT).split(";");
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("charset=")) {
                    String cs = part.substring("charset=".length()).trim();
                    try {
                        return Charset.forName(cs);
                    } catch (Exception ignored) {
                        // 无效字符集，回退 UTF-8
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
