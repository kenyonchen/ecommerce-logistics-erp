package site.hexaarch.ecommerce.logistics.interfaces.controller.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.config.SystemConfigApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.tenant.AccountLoginGuard;
import site.hexaarch.ecommerce.logistics.application.service.tenant.LoginSecurityService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.JwtTokenProvider;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证控制器，用于处理用户登录、刷新令牌、登出和当前用户信息查询。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "认证相关的API接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final LoginSecurityService loginSecurityService;
    private final SystemConfigApplicationService systemConfigApplicationService;
    private final AccountLoginGuard accountLoginGuard;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          UserRepository userRepository,
                          LoginSecurityService loginSecurityService,
                          SystemConfigApplicationService systemConfigApplicationService,
                          AccountLoginGuard accountLoginGuard) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.loginSecurityService = loginSecurityService;
        this.systemConfigApplicationService = systemConfigApplicationService;
        this.accountLoginGuard = accountLoginGuard;
    }

    /**
     * 登录请求
     */
    @Operation(summary = "用户登录", description = "用户登录获取访问令牌，令牌中携带租户ID与角色信息")
    @PostMapping("/login")
    public ResponseEntity<Result<?>> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        // 失败锁定在认证之前：达到阈值直接拒绝，不再给爆破者打认证链路
        loginSecurityService.assertLoginAllowed(username);

        Authentication authentication;
        User user;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException("用户不存在: " + username));
        } catch (RuntimeException failure) {
            // BR-12：认证失败同样是安全事件，爆破与撞库的信号全在这一侧
            loginSecurityService.recordLoginAttempt(username, null, failure);
            throw failure;
        }
        loginSecurityService.recordLoginAttempt(username, user.getTenantId(), null);

        List<String> roles = new ArrayList<>();
        List<String> permissions = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authorityName = authority.getAuthority();
            if (authorityName == null || authorityName.isBlank()) {
                continue;
            }
            if (authorityName.startsWith(JwtTokenProvider.ROLE_PREFIX)) {
                roles.add(authorityName.substring(JwtTokenProvider.ROLE_PREFIX.length()));
            } else {
                permissions.add(authorityName);
            }
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getTenantId(),
                roles.toArray(new String[0]), permissions.toArray(new String[0]),
                sessionTimeoutMillis());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return ResponseEntity.ok(Result.success(new TokenResponse(token, refreshToken,
                user.getUsername(), user.getTenantId(), roles)));
    }

    /**
     * 刷新令牌：校验刷新令牌合法性后签发新的访问令牌
     */
    @Operation(summary = "刷新令牌", description = "使用刷新令牌换取新的访问令牌")
    @PostMapping("/refresh")
    public ResponseEntity<Result<?>> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();
        String username = resolveRefreshSubject(refreshToken);
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BusinessException("刷新令牌不能为空");
            }
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                throw new BusinessException("刷新令牌无效或已过期，请重新登录");
            }
            if (username == null || username.isBlank()) {
                throw new BusinessException("刷新令牌缺少用户信息，请重新登录");
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException("用户不存在: " + username));
            // 续期链路绕过了认证（不经过 UserDetailsService），账号/租户被停用后必须在这里挡住，
            // 否则刷新令牌可以无限续期一个「名义上已停用」的会话
            if (!accountLoginGuard.isUsable(user)) {
                throw new BusinessException("账号不可用：" + accountLoginGuard.describeBlocker(user) + "，请重新登录");
            }

            List<String> roles = new ArrayList<>();
            List<String> permissions = new ArrayList<>();
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> {
                    if (role.getName() != null) {
                        roles.add(role.getName());
                    }
                    if (role.getPermissions() != null) {
                        role.getPermissions().stream()
                                .filter(p -> p != null && !p.isBlank())
                                .forEach(permissions::add);
                    }
                });
            }

            String newToken = jwtTokenProvider.generateToken(user.getUsername(), user.getTenantId(),
                    roles.toArray(new String[0]), permissions.toArray(new String[0]), sessionTimeoutMillis());
            loginSecurityService.recordRefreshAttempt(username, user.getTenantId(), null);
            return ResponseEntity.ok(Result.success(new TokenResponse(newToken, refreshToken,
                    user.getUsername(), user.getTenantId(), roles)));
        } catch (RuntimeException failure) {
            loginSecurityService.recordRefreshAttempt(username, null, failure);
            throw failure;
        }
    }

    /**
     * 登出（无状态JWT：客户端删除令牌即可完成登出，服务端清理安全上下文）
     */
    @Operation(summary = "用户登出", description = "用户登出操作")
    @PostMapping("/logout")
    @Auditable(action = AuditActions.AUTH_LOGOUT, label = "用户登出", targetType = "AuthSession")
    public ResponseEntity<Result<?>> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Result.success("登出成功"));
    }

    /**
     * 访问令牌有效期取系统配置的会话超时（秒）：让界面上的安全参数真的作用到鉴权上
     */
    private long sessionTimeoutMillis() {
        return systemConfigApplicationService.sessionTimeoutSeconds() * 1000L;
    }

    /**
     * 刷新令牌里的主体名只用于留痕；令牌本身非法时按匿名处理，非法形态统一在下面拒绝并记失败
     */
    private String resolveRefreshSubject(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            return jwtTokenProvider.getUsernameFromToken(refreshToken);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 查询当前登录用户信息（含租户与角色权限），前端用于恢复会话与权限守卫
     */
    @Operation(summary = "当前用户信息", description = "返回当前登录用户的资料、租户ID与角色权限列表")
    @GetMapping("/me")
    public ResponseEntity<Result<?>> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("未登录或会话已失效");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException("用户不存在: " + authentication.getName()));

        List<RoleView> roles = new ArrayList<>();
        Set<String> permissions = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                roles.add(new RoleView(role.getId(), role.getName(), role.getDescription(),
                        role.getPermissions() == null ? List.of() : new ArrayList<>(role.getPermissions())));
                if (role.getPermissions() != null) {
                    permissions.addAll(role.getPermissions());
                }
            });
        }

        return ResponseEntity.ok(Result.success(new MeResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus() == null ? null : user.getStatus().name(),
                roles,
                new ArrayList<>(permissions)
        )));
    }

    // 请求和响应类
    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class TokenResponse {
        private String token;
        private String refreshToken;
        private String username;
        private String tenantId;
        private List<String> roles;

        public TokenResponse(String token, String refreshToken, String username, String tenantId, List<String> roles) {
            this.token = token;
            this.refreshToken = refreshToken;
            this.username = username;
            this.tenantId = tenantId;
            this.roles = roles;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }

    public static class RoleView {
        private String id;
        private String name;
        private String description;
        private List<String> permissions;

        public RoleView(String id, String name, String description, List<String> permissions) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.permissions = permissions;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }

    public static class MeResponse {
        private String id;
        private String tenantId;
        private String username;
        private String email;
        private String phone;
        private String firstName;
        private String lastName;
        private String status;
        private List<RoleView> roles;
        private List<String> permissions;

        public MeResponse(String id, String tenantId, String username, String email, String phone,
                          String firstName, String lastName, String status,
                          List<RoleView> roles, List<String> permissions) {
            this.id = id;
            this.tenantId = tenantId;
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.firstName = firstName;
            this.lastName = lastName;
            this.status = status;
            this.roles = roles;
            this.permissions = permissions;
        }

        public String getId() {
            return id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getStatus() {
            return status;
        }

        public List<RoleView> getRoles() {
            return roles;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }
}
