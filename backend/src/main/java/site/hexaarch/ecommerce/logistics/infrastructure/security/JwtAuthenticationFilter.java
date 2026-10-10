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
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JWT认证过滤器，用于在每个请求中验证JWT令牌并设置Spring Security上下文。
 * <p>
 * 令牌只用于证明“你是谁”（签名 + 主体用户名）；租户、角色与权限点一律在每次请求时从库中装载，
 * 认证成功后会：
 * 1. 将用户的角色写入 GrantedAuthority（ROLE_ 前缀，供 hasRole/@PreAuthorize 使用）、
 *    角色的权限点原样写入（供 hasAuthority 使用）；
 * 2. 将用户的租户ID写入 {@link TenantContext}（供租户隔离切面与服务层使用）。
 * </p>
 * <p>
 * 之所以不直接信任令牌声明：令牌有效期 24h，删号、停用、改角色、重新分配租户若只反映在库里，
 * 旧令牌会在这一整天里继续按旧身份放行或按已失效的租户空转（冒烟实测过：管理端重新分配租户后，
 * 用户端所有指标静默显示 0，看起来像系统坏了）。用户不存在或状态非 {@link UserStatus#ACTIVE}
 * 时不建立认证上下文，请求由安全链按匿名处理并返回 401。
 * </p>
 *
 * @author kenyon
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsernameFromToken(jwt);

                // 每次请求重新装载用户：签名有效但账号已删除/停用/锁定的一律降级为匿名
                Optional<User> resolved = username == null || username.isBlank()
                        ? Optional.empty()
                        : userRepository.findByUsername(username);
                if (resolved.isEmpty() || resolved.get().getStatus() != UserStatus.ACTIVE) {
                    logger.debug("令牌主体已不可用，按匿名处理请求: " + username);
                } else {
                    User user = resolved.get();
                    String tenantId = user.getTenantId();

                    // 设置租户上下文
                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContext.setTenantId(tenantId);
                    }

                    // 角色写入 ROLE_ 权限（供 hasRole 使用），权限点原样写入（供 hasAuthority 使用）
                    Set<String> authorityNames = new LinkedHashSet<>();
                    for (Role role : user.getRoles() == null ? Set.<Role>of() : user.getRoles()) {
                        if (role == null || role.getName() == null || role.getName().isBlank()) {
                            continue;
                        }
                        authorityNames.add(role.getName().startsWith(JwtTokenProvider.ROLE_PREFIX)
                                ? role.getName() : JwtTokenProvider.ROLE_PREFIX + role.getName());
                        if (role.getPermissions() != null) {
                            role.getPermissions().stream()
                                    .filter(p -> p != null && !p.isBlank())
                                    .forEach(authorityNames::add);
                        }
                    }
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    for (String authority : authorityNames) {
                        authorities.add(new SimpleGrantedAuthority(authority));
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置认证上下文
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
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
