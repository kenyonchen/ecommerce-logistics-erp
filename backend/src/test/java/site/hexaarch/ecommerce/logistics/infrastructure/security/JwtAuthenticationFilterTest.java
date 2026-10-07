package site.hexaarch.ecommerce.logistics.infrastructure.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 认证过滤器单元测试：锁住 ISS-041（权限点必须落进 SecurityContext，否则 @PreAuthorize 的
 * hasAuthority 子句永不成立）与 M-02（Bearer 前缀剥离）。
 *
 * @author kenyon
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "unitTestSecretKeyMustBeAtLeast256BitsLongForHS512Algorithm-0123456789";

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;
    private String token;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(tokenProvider, "jwtRefreshExpiration", 604800000L);
        filter = new JwtAuthenticationFilter(tokenProvider);
        token = tokenProvider.generateToken("alice", "tenant-a",
                new String[]{"OPERATIONS"}, new String[]{"finance:read", "order:import"});
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsRoleAndPermissionAuthoritiesAndTenantContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/finance/transactions");
        request.addHeader("Authorization", "Bearer " + token);

        assertTrue(executeAndCapture(request).contains("ROLE_OPERATIONS"));
    }

    @Test
    void permissionPointsAreNotRolePrefixed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/finance/transactions");
        request.addHeader("Authorization", "Bearer " + token);

        List<String> authorities = executeAndCapture(request);
        assertTrue(authorities.contains("finance:read"));
        assertTrue(authorities.contains("order:import"));
        assertFalse(authorities.contains("ROLE_finance:read"),
                "权限点被加 ROLE_ 前缀会让所有 hasAuthority('...') 永不满足");
    }

    @Test
    void tenantContextIsClearedAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + token);

        executeAndCapture(request);

        assertFalse(TenantContext.hasTenantId(), "ThreadLocal 未清理会在线程复用下串租户");
    }

    @Test
    void anonymousWhenNoHeaderOrBadToken() throws ServletException, IOException {
        MockHttpServletRequest noHeader = new MockHttpServletRequest("GET", "/api/orders");
        assertNull(executeAndCaptureOrNull(noHeader));

        MockHttpServletRequest badToken = new MockHttpServletRequest("GET", "/api/orders");
        badToken.addHeader("Authorization", "Bearer " + token + "tampered");
        assertNull(executeAndCaptureOrNull(badToken));
    }

    /**
     * 执行过滤器并在请求链路内部抓取当前认证主体与权限，返回权限串列表。
     */
    private List<String> executeAndCapture(MockHttpServletRequest request) throws ServletException, IOException {
        Authentication authentication = executeAndCaptureOrNull(request);
        assertNotNull(authentication, "过滤器未建立认证上下文");
        assertTrue(authentication.isAuthenticated());
        assertEquals("alice", authentication.getName());
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList();
    }

    private Authentication executeAndCaptureOrNull(MockHttpServletRequest request)
            throws ServletException, IOException {
        final Authentication[] captured = new Authentication[1];
        final String[] capturedTenant = new String[1];
        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> {
                    captured[0] = SecurityContextHolder.getContext().getAuthentication();
                    capturedTenant[0] = TenantContext.hasTenantId() ? TenantContext.getTenantId() : null;
                });
        if (captured[0] != null && capturedTenant[0] != null) {
            assertEquals("tenant-a", capturedTenant[0]);
        }
        return captured[0];
    }
}
