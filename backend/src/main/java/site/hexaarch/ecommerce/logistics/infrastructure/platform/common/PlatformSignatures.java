package site.hexaarch.ecommerce.logistics.infrastructure.platform.common;

import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 平台请求签名原语。
 * <p>
 * 各家签名的<b>拼接规则</b>属于平台侧知识，写在各自的适配器里；这里只提供可复算的摘要原语
 * （MD5 / HMAC-MD5 / HMAC-SHA256 的 hex 输出）和 TOP 系的「按 key 字典序拼 k+v」工具。
 * <p>
 * 大小写口径是真实踩过坑的地方：TOP 系文档明确「正确签名应该为32大写字符串」，
 * 而 Shopee / TikTok Shop 要小写 hex，所以这里把大小写显式暴露成两个方法而不是猜一个。
 *
 * @author kenyon
 */
public final class PlatformSignatures {

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private PlatformSignatures() {
    }

    public static String md5Hex(String text) {
        return hex(digest("MD5", text), false);
    }

    /**
     * TOP 系（速卖通 / 淘宝网关）口径：32 位<b>大写</b> hex。
     */
    public static String md5UpperHex(String text) {
        return hex(digest("MD5", text), true);
    }

    public static String hmacMd5Hex(String key, String text) {
        return hex(mac("HmacMD5", key, text), false);
    }

    public static String hmacMd5UpperHex(String key, String text) {
        return hex(mac("HmacMD5", key, text), true);
    }

    public static String hmacSha256Hex(String key, String text) {
        return hex(mac("HmacSHA256", key, text), false);
    }

    public static String hmacSha256UpperHex(String key, String text) {
        return hex(mac("HmacSHA256", key, text), true);
    }

    public static String sha256Hex(String text) {
        return hex(digest("SHA-256", text), false);
    }

    /**
     * TOP 签名串的原文部分：按参数名 ASCII 字典序排列后，依次拼接 {@code key+value}（无分隔符），
     * 跳过值为空与签名参数本身。
     * <p>
     * 官方协议页明确「除去sign参数和byte[]类型的参数」，且 {@code md5} 方式要在串首尾各包一次 secret，
     * {@code hmac} 方式不包 secret（secret 是 HMAC 的密钥）。
     */
    public static String topConcat(Map<String, String> params, String signParamName) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name.equalsIgnoreCase(signParamName) || value == null || value.isBlank()) {
                continue;
            }
            builder.append(name).append(value);
        }
        return builder.toString();
    }

    /**
     * TOP 系签名：{@code md5(secret + 字典序k+v拼接 + secret)} 取 32 位大写，
     * {@code hmac} 为 {@code hmac_md5(字典序k+v拼接)} 以 secret 作密钥，同样大写。
     *
     * @param signMethod {@code md5} / {@code hmac} / {@code hmac-sha256}（TOP 的 sign_method 取值）
     */
    public static String topSign(Map<String, String> params, String secret, String signMethod) {
        String concat = topConcat(params, "sign");
        String method = signMethod == null ? "md5" : signMethod.trim().toLowerCase();
        return switch (method) {
            case "md5" -> md5UpperHex(secret + concat + secret);
            case "hmac", "hmac-md5" -> hmacMd5UpperHex(secret, concat);
            // 公开文档页未展开 hmac-sha256 的拼接细则，沿用 TOP 的 HMAC 分支口径（不包 secret、大写）
            case "hmac-sha256", "hmacsha256", "sha256" -> hmacSha256UpperHex(secret, concat);
            default -> throw new BusinessException("不支持的 TOP 签名方式: " + signMethod
                    + "（可选 md5 / hmac / hmac-sha256）", "PLATFORM_SIGN_METHOD_UNSUPPORTED");
        };
    }

    private static byte[] digest(String algorithm, String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            return messageDigest.digest(bytes(text));
        } catch (Exception e) {
            throw new BusinessException(algorithm + " 计算失败: " + e.getMessage(), "PLATFORM_SIGN_FAILED");
        }
    }

    private static byte[] mac(String algorithm, String key, String text) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(bytes(key), algorithm));
            return mac.doFinal(bytes(text));
        } catch (Exception e) {
            throw new BusinessException(algorithm + " 计算失败: " + e.getMessage(), "PLATFORM_SIGN_FAILED");
        }
    }

    private static byte[] bytes(String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] raw, boolean upper) {
        char[] digits = new char[raw.length * 2];
        int index = 0;
        for (byte b : raw) {
            digits[index++] = HEX_LOWER[(b >> 4) & 0x0F];
            digits[index++] = HEX_LOWER[b & 0x0F];
        }
        String hex = new String(digits);
        return upper ? hex.toUpperCase() : hex;
    }
}
