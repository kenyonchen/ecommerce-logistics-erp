package site.hexaarch.ecommerce.logistics.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 平台凭据信封加密器（AES-256-GCM）。
 * <p>
 * 存储格式：{@code enc:v1:base64(iv[12] || ciphertext || gcmTag)}。无该前缀的值按历史明文处理并原样返回，
 * 使既有数据在不强制迁移的前提下仍可读取（下次写入时自动加密收敛）。
 * </p>
 * <p>
 * 主密钥只从环境变量/外部化配置 {@code platform.credential.master-key} 读取（ADR-0009），
 * 不入库、不入 Git。未配置时加密关闭并打 WARN——此时行为与修复前一致，不会让应用起不来。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class CredentialCipher {

    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(@Value("${platform.credential.master-key:}") String configuredKey) {
        this.masterKey = resolveKey(configuredKey);
        if (this.masterKey == null) {
            log.warn("[CredentialCipher] 未配置 platform.credential.master-key，平台凭据将以明文存储（ISS-011 未关闭）。"
                    + "生产环境请通过环境变量 PLATFORM_CREDENTIAL_MASTER_KEY 注入 32 字节密钥的 Base64。");
        }
    }

    public boolean isEnabled() {
        return masterKey != null;
    }

    /**
     * 加密明文凭据。空值与已加密值原样返回，避免二次加密。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || isEncrypted(plaintext) || masterKey == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            // 加密失败绝不能把明文悄悄写进库
            throw new IllegalStateException("平台凭据加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密存储值；无 {@code enc:v1:} 前缀时视为历史明文原样返回。
     */
    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        if (masterKey == null) {
            throw new IllegalStateException("凭据已加密但未配置 platform.credential.master-key，无法解密");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("平台凭据解密失败（主密钥是否与写入时一致？）: " + e.getMessage(), e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Base64 解出 32 字节则直接使用；否则按口令 SHA-256 派生（便于本地开发，生产建议给标准 Base64 密钥）。
     */
    private static SecretKeySpec resolveKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length == 32) {
                return new SecretKeySpec(decoded, "AES");
            }
        } catch (IllegalArgumentException ignored) {
            // 不是 Base64，落到口令派生分支
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.trim().getBytes(StandardCharsets.UTF_8));
            log.warn("[CredentialCipher] master-key 不是 32 字节 Base64，已按 SHA-256 口令派生；生产环境请改用随机密钥的 Base64");
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("platform.credential.master-key 无法解析为 AES-256 密钥: " + e.getMessage(), e);
        }
    }
}
