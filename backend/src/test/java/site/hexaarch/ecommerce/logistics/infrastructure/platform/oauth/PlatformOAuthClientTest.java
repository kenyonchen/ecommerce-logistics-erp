package site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformHttpExchange;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@code PlatformOAuthClient#parse} 的契约测试：令牌信封的五家形态差异都在这里收敛，
 * 到期时间算错的后果是「每次外呼都白白刷新一次令牌」，所以逐个键形各锁一条。
 * <p>
 * fixture 验证，不需要真实凭据（ISS-064）。
 *
 * @author kenyon
 */
class PlatformOAuthClientTest {

    private final PlatformOAuthClient client = new PlatformOAuthClient(mock(PlatformHttpExchange.class));

    @Test
    void shouldReadSnakeCaseExpirySecondsAsRelativeTtl() {
        PlatformToken token = client.parse("{\"access_token\":\"AT\",\"expires_in\":3600}", "Test");

        assertEquals("AT", token.accessToken());
        assertNotNull(token.expiresAt(), "expires_in 必须换算出到期时间，否则令牌永远被判为已过期");
        assertTrue(token.expiresAt().isAfter(LocalDateTime.now().plusSeconds(3590))
                        && token.expiresAt().isBefore(LocalDateTime.now().plusSeconds(3610)),
                "到期时间应为 now+3600s，实际 " + token.expiresAt());
    }

    @Test
    void shouldReadCamelCaseExpirySecondsAsSiblingNotNestedPath() {
        PlatformToken token = client.parse("{\"accessToken\":\"AT\",\"expiresIn\":7200}", "Test");

        assertEquals("AT", token.accessToken());
        assertNotNull(token.expiresAt(), "camelCase 是兄弟候选键，不是 expires_in 的下钻目标");
        assertTrue(token.expiresAt().isAfter(LocalDateTime.now().plusSeconds(7190)),
                "到期时间应为 now+7200s，实际 " + token.expiresAt());
    }

    @Test
    void shouldReadAbsoluteEpochExpiryAsUtcWallClock() {
        long expireTime = LocalDateTime.of(2026, 10, 9, 12, 0, 0)
                .toEpochSecond(ZoneOffset.UTC);

        PlatformToken token = client.parse("{\"access_token\":\"AT\",\"expire_time\":" + expireTime + "}", "Shopee");

        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0, 0), token.expiresAt());
    }

    @Test
    void shouldReadMillisecondEpochExpiryWithoutRollingIntoFarFuture() {
        long expireTimeMillis = LocalDateTime.of(2026, 10, 9, 12, 0, 0)
                .toEpochSecond(ZoneOffset.UTC) * 1000L;

        PlatformToken token = client.parse("{\"access_token\":\"AT\",\"expire_time\":" + expireTimeMillis + "}", "Shopee");

        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0, 0), token.expiresAt(),
                "13 位毫秒时间戳按毫秒处理，不能解析成公元 4 万年");
    }

    @Test
    void shouldUnwrapTokenFromDataEnvelope() {
        PlatformToken token = client.parse(
                "{\"code\":0,\"message\":\"success\",\"data\":{\"access_token\":\"AT\",\"expires_in\":60,"
                        + "\"refresh_token\":\"RT\"}}", "TikTok Shop");

        assertEquals("AT", token.accessToken());
        assertEquals("RT", token.refreshToken());
        assertNotNull(token.expiresAt());
    }

    @Test
    void shouldKeepExpiryNullWhenPlatformGivesNoExpiryField() {
        PlatformToken token = client.parse("{\"access_token\":\"AT\"}", "Test");

        assertNull(token.expiresAt(), "没有到期字段就是不知道到期时间，不能凭空造一个 TTL");
    }

    @Test
    void shouldFailWithPlatformErrorFieldsWhenAccessTokenMissing() {
        BusinessException exception = assertThrows(BusinessException.class, () -> client.parse(
                "{\"error\":\"invalid_grant\",\"error_description\":\"refresh token has been used\","
                        + "\"access_token\":\"\"}", "Mercado Libre"));

        assertEquals("PLATFORM_TOKEN_RESPONSE_INVALID", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("invalid_grant"),
                "错误体要能排障: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("refresh token has been used"),
                "错误体要能排障: " + exception.getMessage());
    }
}
