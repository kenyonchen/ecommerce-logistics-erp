package site.hexaarch.ecommerce.logistics.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT认证过滤器，用于在每个请求中验证JWT令牌并设置Spring Security上下文。
 * <p>
 * 认证成功后会：
 * 1. 将令牌中的角色写入 GrantedAuthority（供 hasRole/@PreAuthorize 使用）；
 * 2. 将令牌中的租户ID写入 {@link TenantContext}（供租户隔离切面与服务层使用）。
 * </p>
 *
 * @author kenyon
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsernameFromToken(jwt);
                String tenantId = jwtTokenProvider.getTenantIdFromToken(jwt);
                List<String> roles = jwtTokenProvider.getRolesFromToken(jwt);
                List<String> permissions = jwtTokenProvider.getPermissionsFromToken(jwt);

                // 设置租户上下文
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setTenantId(tenantId);
                }

                // 角色写入 ROLE_ 权限（供 hasRole 使用），权限点原样写入（供 hasAuthority 使用）
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String role : roles) {
                    if (role != null && !role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(
                                role.startsWith(JwtTokenProvider.ROLE_PREFIX)
                                        ? role : JwtTokenProvider.ROLE_PREFIX + role));
                    }
                }
                for (String permission : permissions) {
                    if (permission != null && !permission.isBlank()
                            && authorities.stream().noneMatch(a -> permission.equals(a.getAuthority()))) {
                        authorities.add(new SimpleGrantedAuthority(permission));
                    }
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 设置认证上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Failed to set user authentication in security context", e);
        } finally {
            chain.doFilter(request, response);
            // 请求结束后清理租户上下文，避免线程复用导致的数据串租户
            TenantContext.clearTenantId();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
