package site.hexaarch.ecommerce.logistics.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 令牌提供者单元测试：重点锁住 ISS-041 的回归——角色与权限点都必须能在令牌往返后取回。
 *
 * @author kenyon
 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "unitTestSecretKeyMustBeAtLeast256BitsLongForHS512Algorithm-0123456789";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(provider, "jwtRefreshExpiration", 604800000L);
    }

    @Test
    void rolesAndPermissionsSurviveTokenRoundTrip() {
        String token = provider.generateToken("alice", "tenant-a",
                new String[]{"OPERATIONS"}, new String[]{"finance:read", "order:import"});

        assertEquals(List.of("OPERATIONS"), provider.getRolesFromToken(token));
        assertEquals(List.of("finance:read", "order:import"), provider.getPermissionsFromToken(token));
        assertEquals("alice", provider.getUsernameFromToken(token));
        assertEquals("tenant-a", provider.getTenantIdFromToken(token));
        assertTrue(provider.validateToken(token));
    }

    @Test
    void generateFromAuthenticationSplitsRoleAndPermissionAuthorities() {
        var userDetails = new org.springframework.security.core.userdetails.User(
                "bob", "ignored",
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                        new SimpleGrantedAuthority("finance:write")));
        var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = provider.generateToken(authentication);

        assertEquals(List.of("PLATFORM_ADMIN"), provider.getRolesFromToken(token));
        assertEquals(List.of("finance:write"), provider.getPermissionsFromToken(token));
    }

    @Test
    void tamperedAndBlankTokensAreRejected() {
        String token = provider.generateToken("alice", "tenant-a",
                new String[]{"OPERATIONS"}, new String[]{"finance:read"});

        assertFalse(provider.validateToken(token + "x"));
        assertFalse(provider.validateToken("not-a-jwt"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
        assertEquals(List.of(), provider.getRolesFromToken(token + "x"));
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(provider, "jwtExpiration", -1000L);

        String token = provider.generateToken("alice", "tenant-a",
                new String[]{"OPERATIONS"}, new String[]{});

        assertFalse(provider.validateToken(token));
    }
}
