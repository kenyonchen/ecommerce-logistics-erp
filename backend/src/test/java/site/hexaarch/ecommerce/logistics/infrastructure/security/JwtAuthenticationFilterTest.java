package site.hexaarch.ecommerce.logistics.infrastructure.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 认证过滤器单元测试：锁住 ISS-041（权限点必须落进 SecurityContext，否则 @PreAuthorize 的
 * hasAuthority 子句永不成立）、M-02（Bearer 前缀剥离）与 ISS-099（认证声明以库为准，
 * 令牌只承载身份；用户被删/停用/换租户/改角色后旧令牌不得继续按旧声明服务）。
 *
 * @author kenyon
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "unitTestSecretKeyMustBeAtLeast256BitsLongForHS512Algorithm-0123456789";

    private JwtTokenProvider tokenProvider;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;
    private String token;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(tokenProvider, "jwtRefreshExpiration", 604800000L);
        userRepository = Mockito.mock(UserRepository.class);
        Mockito.when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(dbUser("tenant-a", UserStatus.ACTIVE, "OPERATIONS",
                        "finance:read", "order:import")));
        filter = new JwtAuthenticationFilter(tokenProvider, userRepository);
        token = tokenProvider.generateToken("alice", "tenant-a",
                new String[]{"OPERATIONS"}, new String[]{"finance:read", "order:import"});
        SecurityContextHolder.clearContext();
    }

    private User dbUser(String tenantId, UserStatus status, String roleName, String... permissions) {
        User user = User.create(tenantId, "alice", "hashed", "alice@example.com",
                null, "Alice", "Doe");
        if (status != UserStatus.ACTIVE) {
            switch (status) {
                case INACTIVE -> user.deactivate();
                case LOCKED -> user.lock();
                default -> user.activate();
            }
        }
        if (roleName != null) {
            Role role = Role.createSystemRole(roleName, "单元测试角色");
            for (String permission : permissions) {
                role.addPermission(permission);
            }
            user.addRole(role);
        }
        return user;
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

    @Test
    void tenantContextComesFromDatabaseWhenTokenClaimIsStale() throws ServletException, IOException {
        // 冒烟实测：管理员重新分配租户（或库被重建）后，旧令牌里的 tenantId 声明已失效，
        // 但签名仍有效 → 过滤器照旧写入上下文，页面全部指标静默显示 0，用户以为系统坏了。
        Mockito.when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(dbUser("tenant-db", UserStatus.ACTIVE, "OPERATIONS",
                        "finance:read")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/overview");
        request.addHeader("Authorization", "Bearer " + token);
        String[] tenantSeen = new String[1];
        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> tenantSeen[0] = TenantContext.hasTenantId()
                        ? TenantContext.getTenantId() : null);

        assertEquals("tenant-db", tenantSeen[0], "租户声明必须以库为准，否则旧令牌会按已失效的租户空转");
    }

    @Test
    void authoritiesComeFromDatabaseSoRevokedRolesTakeEffectImmediately() throws ServletException, IOException {
        Mockito.when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(dbUser("tenant-a", UserStatus.ACTIVE, "VIEWER",
                        "order:read")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + token);

        List<String> authorities = executeAndCapture(request);
        assertTrue(authorities.contains("ROLE_VIEWER"));
        assertFalse(authorities.contains("ROLE_OPERATIONS"),
                "令牌有效期 24h，权限若只取令牌声明则撤权后仍会放行一整天");
    }

    @Test
    void anonymousWhenUserNoLongerExistsOrIsNotActive() throws ServletException, IOException {
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + token);
        assertNull(executeAndCaptureOrNull(request), "用户已删除时旧令牌必须降级为匿名");

        Mockito.when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(dbUser("tenant-a", UserStatus.LOCKED, "OPERATIONS")));
        assertNull(executeAndCaptureOrNull(request), "被锁定的用户必须立即失去访问");

        Mockito.when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(dbUser("tenant-a", UserStatus.INACTIVE, "OPERATIONS")));
        assertNull(executeAndCaptureOrNull(request), "被禁用的用户必须立即失去访问");
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
