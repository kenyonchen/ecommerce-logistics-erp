package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;

/**
 * 外呼失败的措辞来源：把"异常族"翻译成一句可据此排查的话。
 * <p>
 * JDK HttpClient 在连接阶段抛的 {@code ResourceAccessException} 自身消息形如
 * {@code I/O error on GET request for "http://…": null}——真正的原因（DNS 解析不了 / 端口不通 /
 * 超时）躺在 {@code getCause()} 链里且消息为 null。传输层此前只取 {@code getMessage()}，
 * 于是"地址写错"被重试日志和最终提示一起丢成"限流或平台不可用"（ISS-131）。
 * </p>
 * <p>
 * 两个出口口径不同，是刻意的：{@link #causeChain} 给**服务端日志**，可以带 URI；
 * {@link #stageHint} 给**租户用户**，只说族别与建议，不回显主机名与端口。
 * </p>
 *
 * @author kenyon
 */
public final class PlatformFailures {

    private PlatformFailures() {
    }

    /**
     * 完整 cause 链摘要，形如 {@code ResourceAccessException: I/O error … <- ConnectException <- …}。
     * 消息为 null 的节点只留类名，Spring 那种以 ": null" 结尾的消息也会被去掉尾巴。
     */
    public static String causeChain(Throwable error) {
        if (error == null) {
            return "无（未记录到具体异常）";
        }
        StringBuilder chain = new StringBuilder();
        Throwable cursor = error;
        for (int depth = 0; cursor != null && depth < 8; depth++) {
            if (depth > 0) {
                chain.append(" <- ");
            }
            chain.append(cursor.getClass().getSimpleName());
            String message = trimNullTail(cursor.getMessage());
            if (message != null) {
                chain.append(": ").append(message);
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return chain.toString();
    }

    /**
     * 链上最深那个节点的摘要（真正的原因），只含类名与它自己的消息，不含外层包装里的 URI。
     */
    public static String rootCauseSummary(Throwable error) {
        if (error == null) {
            return "未知原因";
        }
        Throwable deepest = error;
        for (int depth = 0; depth < 8 && deepest.getCause() != null && deepest.getCause() != deepest; depth++) {
            deepest = deepest.getCause();
        }
        String message = trimNullTail(deepest.getMessage());
        return message == null ? deepest.getClass().getSimpleName()
                : deepest.getClass().getSimpleName() + ": " + message;
    }

    /**
     * 按异常族给出面向使用者的原因；认不出族时返回 {@code null}（由调用方兜通用文案）。
     * <p>
     * **判定顺序有讲究**：实测 DNS 解析失败与端口拒连的链里都出现 {@code ConnectException}
     * （{@code ConnectException <- UnresolvedAddressException} / {@code ConnectException <- ClosedChannelException}），
     * 所以必须先看叶子侧的 DNS/超时标记，再看"连不上"，否则两者会塌成同一句话。
     * </p>
     */
    public static String stageHint(Throwable error) {
        if (error == null) {
            return null;
        }
        List<Class<?>> chain = classesOf(error);
        if (containsAny(chain, UnresolvedAddressException.class, UnknownHostException.class)) {
            return "目标地址无法解析（域名写错或 DNS 不可用），请检查店铺地址(storeUrl)";
        }
        if (containsAny(chain, SocketTimeoutException.class, java.net.http.HttpTimeoutException.class)) {
            return "连接或响应超时（平台未在超时内返回），请稍后重试或缩短查询范围";
        }
        if (containsAny(chain, SSLException.class, java.security.cert.CertificateException.class)) {
            return "TLS 握手失败（证书不受信任或协议不匹配）";
        }
        if (containsAny(chain, ConnectException.class, ClosedChannelException.class,
                NoRouteToHostException.class, PortUnreachableException.class, SocketException.class)) {
            return "无法连接到目标地址（端口不通、服务未启动或网络不通），请检查店铺地址(storeUrl)";
        }
        if (error instanceof HttpClientErrorException.TooManyRequests) {
            return "平台限流(429)，重试后仍未恢复";
        }
        if (error instanceof HttpServerErrorException serverError) {
            return "平台侧错误(HTTP " + serverError.getStatusCode().value() + ")";
        }
        if (error instanceof HttpClientErrorException clientError) {
            return "平台拒绝请求(HTTP " + clientError.getStatusCode().value() + ")";
        }
        if (error instanceof RestClientResponseException responseError) {
            return "平台返回 HTTP " + responseError.getStatusCode().value();
        }
        return null;
    }

    /**
     * 重试耗尽后的最终提示：能说清族就别再说"限流或平台不可用"。
     */
    public static String exhausted(String label, int maxAttempts, Throwable lastFailure) {
        String hint = stageHint(lastFailure);
        return label + " 连续 " + maxAttempts + " 次调用未成功（"
                + (hint != null ? hint : "限流或平台不可用") + "）";
    }

    /**
     * 面向使用者的单因由：先按族，认不出再取消息本身（去掉 ": null" 尾巴），都为空时退回类名。
     */
    public static String userReason(Throwable error) {
        if (error == null) {
            return "未知原因";
        }
        String hint = stageHint(error);
        if (hint != null) {
            return hint;
        }
        String message = trimNullTail(error.getMessage());
        return message != null ? message : error.getClass().getSimpleName();
    }

    private static List<Class<?>> classesOf(Throwable error) {
        List<Class<?>> chain = new ArrayList<>();
        Throwable cursor = error;
        for (int depth = 0; cursor != null && depth < 8; depth++) {
            chain.add(cursor.getClass());
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return chain;
    }

    private static boolean containsAny(List<Class<?>> chain, Class<?>... families) {
        for (Class<?> node : chain) {
            for (Class<?> family : families) {
                if (family.isAssignableFrom(node)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Spring 把 cause 的 null 消息拼进了自身消息（{@code …": null}），折行后尾巴会误导读者。
     */
    private static String trimNullTail(String message) {
        if (message == null) {
            return null;
        }
        String collapsed = message.replaceAll("\\s+", " ").trim();
        if (collapsed.endsWith(": null")) {
            collapsed = collapsed.substring(0, collapsed.length() - ": null".length()).trim();
        }
        return collapsed.isEmpty() || "null".equals(collapsed) ? null : collapsed;
    }
}
