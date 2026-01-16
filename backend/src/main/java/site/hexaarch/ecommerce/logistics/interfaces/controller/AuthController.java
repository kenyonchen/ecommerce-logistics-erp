package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.infrastructure.security.JwtTokenProvider;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

/**
 * 认证控制器，用于处理用户登录、刷新令牌和登出等认证操作。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "认证相关的API接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 登录请求
     */
    @Operation(summary = "用户登录", description = "用户登录获取访问令牌")
    @PostMapping("/login")
    public ResponseEntity<Result<?>> login(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(authentication);

        return ResponseEntity.ok(Result.success(new TokenResponse(token)));
    }

    /**
     * 刷新令牌
     */
    @Operation(summary = "刷新令牌", description = "使用现有令牌刷新获取新令牌")
    @PostMapping("/refresh")
    public ResponseEntity<Result<?>> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        // 实现令牌刷新逻辑
        String newToken = jwtTokenProvider.generateRefreshToken(refreshTokenRequest.getUsername());
        return ResponseEntity.ok(Result.success(new TokenResponse(newToken)));
    }

    /**
     * 登出
     */
    @Operation(summary = "用户登出", description = "用户登出操作")
    @PostMapping("/logout")
    public ResponseEntity<Result<?>> logout() {
        // 实现登出逻辑
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Result.success("登出成功"));
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
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class TokenResponse {
        private String token;

        public TokenResponse(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}