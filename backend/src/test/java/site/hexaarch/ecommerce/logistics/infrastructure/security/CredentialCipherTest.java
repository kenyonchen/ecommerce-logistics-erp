package site.hexaarch.ecommerce.logistics.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台凭据加密器契约测试（ISS-011 / ADR-0009）。
 *
 * @author kenyon
 */
class CredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CredentialCipher cipher = new CredentialCipher(KEY);

    @Test
    void shouldRoundTripPlaintextThroughCiphertext() {
        String stored = cipher.encrypt("shpat_abcdef123456");

        assertTrue(CredentialCipher.isEncrypted(stored), "落库值必须带 enc:v1: 前缀");
        assertNotEquals("shpat_abcdef123456", stored);
        assertEquals("shpat_abcdef123456", cipher.decrypt(stored));
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        // AES-GCM 每次换新 IV：同样的明文不能产生同样的密文，否则能从库里的重复值反推凭据
        assertNotEquals(cipher.encrypt("same-secret"), cipher.encrypt("same-secret"));
    }

    @Test
    void shouldNotDoubleEncryptStoredValue() {
        String once = cipher.encrypt("same-secret");
        assertEquals(once, cipher.encrypt(once));
    }

    @Test
    void shouldReadLegacyPlaintextRowsWithoutMigration() {
        assertEquals("legacy-plain", cipher.decrypt("legacy-plain"));
    }

    @Test
    void shouldPassThroughBlankValues() {
        assertNull(cipher.encrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertEquals("   ", cipher.encrypt("   "));
    }

    @Test
    void shouldDisableEncryptionButNeverCorruptValueWhenKeyMissing() {
        CredentialCipher disabled = new CredentialCipher("");

        assertFalse(disabled.isEnabled());
        // 未配主密钥时行为与修复前一致：原样存，绝不能存成半截密文
        assertEquals("plain", disabled.encrypt("plain"));
        assertEquals("plain", disabled.decrypt("plain"));
    }

    @Test
    void shouldFailLoudlyWhenReadingEncryptedRowWithoutKey() {
        CredentialCipher disabled = new CredentialCipher("");
        String stored = cipher.encrypt("plain");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> disabled.decrypt(stored));
        assertTrue(error.getMessage().contains("master-key"), "报错要指出缺的是主密钥");
    }

    @Test
    void shouldRejectWrongMasterKeyInsteadOfReturningGarbage() {
        String stored = cipher.encrypt("plain");
        CredentialCipher other = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[31]));

        assertThrows(IllegalStateException.class, () -> other.decrypt(stored));
    }

    @Test
    void shouldDeriveKeyFromPassphraseForLocalDevelopment() {
        CredentialCipher derived = new CredentialCipher("local-dev-passphrase");

        assertTrue(derived.isEnabled());
        assertEquals("v", derived.decrypt(derived.encrypt("v")));
    }
}
