package site.hexaarch.ecommerce.logistics.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.infrastructure.security.JwtAuthenticationFilter;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security配置类
 * <p>
 * 授权模型：
 * - /api/auth/login|refresh|logout 公开；
 * - /v3/api-docs/**、/swagger-ui/** 公开（冒烟清单 0.3 要求文档可用）；
 * - /api/auth/me、/api/users/**、/api/roles/**、/api/platform-integrations/** 需认证；
 * - /api/tenants/** 仅平台管理员（ROLE_PLATFORM_ADMIN）；
 * - 其余请求需认证。
 * </p>
 * <p>
 * 未认证出口是 401 + {@link Result} 信封，权限不足是 403 + 同一信封；
 * 默认的 403 空 body 会让前端"401 跳登录"分支永远不可达。
 * </p>
 *
 * @author kenyon
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_PLATFORM_ADMIN = RoleNames.PLATFORM_ADMIN;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // 平台管理端运行在 5110，卖家业务端运行在 5120（与两个前端 vite.config.js 的 server.port 一致）
        corsConfig.setAllowedOrigins(List.of("http://localhost:5110", "http://localhost:5120"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        return request -> corsConfig;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
                                                  ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // 接口文档按冒烟清单 0.3 需要可访问；生产用 springdoc.api-docs.enabled=false 关闭
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/tenants/**").hasRole(ROLE_PLATFORM_ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                // 未认证/权限不足都要走统一信封：默认出口是 403 空 body，
                // 前端 api.js 的 401→/login 分支因此不可达（ISS-032/058）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeEnvelope(response, HttpStatus.UNAUTHORIZED,
                                        Result.error("UNAUTHORIZED", "未认证或登录已过期"), objectMapper))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeEnvelope(response, HttpStatus.FORBIDDEN,
                                        Result.error("ACCESS_DENIED", "没有权限执行该操作"), objectMapper))
                );

        // 添加JWT认证过滤器
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeEnvelope(HttpServletResponse response, HttpStatus status,
                                      Result<?> body, ObjectMapper objectMapper) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
