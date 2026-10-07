package site.hexaarch.ecommerce.logistics.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT令牌提供者，用于生成和验证JWT令牌。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String ROLE_PREFIX = "ROLE_";

    @Value("${jwt.secret:#{'mySecretKeyMustBeAtLeast256BitsLongForHS512Algorithm'}}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 默认24小时
    private long jwtExpiration;

    @Value("${jwt.refresh.expiration:604800000}") // 默认7天
    private long jwtRefreshExpiration;

    private SecretKey getSigningKey() {
        // 使用JJWT提供的密钥构建器确保密钥长度足够安全
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT令牌（不含租户ID，租户信息由认证后补全）
     *
     * @param authentication 认证对象
     * @return JWT令牌
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = new ArrayList<>();
        List<String> permissions = new ArrayList<>();
        for (GrantedAuthority authority : userDetails.getAuthorities()) {
            String name = authority.getAuthority();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.startsWith(ROLE_PREFIX)) {
                roles.add(name.substring(ROLE_PREFIX.length()));
            } else {
                permissions.add(name);
            }
        }
        return generateToken(userDetails.getUsername(), null,
                roles.toArray(new String[0]), permissions.toArray(new String[0]));
    }

    /**
     * 生成带有租户ID的JWT令牌
     *
     * @param username    用户名
     * @param tenantId    租户ID
     * @param roles       角色名列表（不含 ROLE_ 前缀）
     * @param permissions 权限点列表（原样写入，供 hasAuthority 使用）
     * @return JWT令牌
     */
    public String generateToken(String username, String tenantId, String[] roles, String[] permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USERNAME, username);
        if (tenantId != null) {
            claims.put(CLAIM_TENANT_ID, tenantId);
        }
        claims.put(CLAIM_ROLES, roles == null ? new String[0] : roles);
        claims.put(CLAIM_PERMISSIONS, permissions == null ? new String[0] : permissions);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新令牌
     *
     * @param username 用户名
     * @return 刷新令牌
     */
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USERNAME, username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtRefreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从令牌中提取用户名
     *
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get(CLAIM_USERNAME, String.class) : null;
    }

    /**
     * 从令牌中提取租户ID
     *
     * @param token JWT令牌
     * @return 租户ID，可能为null（令牌未携带租户信息）
     */
    public String getTenantIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get(CLAIM_TENANT_ID, String.class) : null;
    }

    /**
     * 从令牌中提取角色列表（角色名，不含 ROLE_ 前缀）
     *
     * @param token JWT令牌
     * @return 角色列表
     */
    public List<String> getRolesFromToken(String token) {
        return stringListClaim(token, CLAIM_ROLES);
    }

    /**
     * 从令牌中提取权限点列表（如 finance:read）
     *
     * @param token JWT令牌
     * @return 权限点列表
     */
    public List<String> getPermissionsFromToken(String token) {
        return stringListClaim(token, CLAIM_PERMISSIONS);
    }

    private List<String> stringListClaim(String token, String claimName) {
        Claims claims = parseClaims(token);
        if (claims == null) {
            return List.of();
        }
        Object value = claims.get(claimName);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    /**
     * 验证令牌（验签 + 过期校验）
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return parseClaims(token) != null;
    }

    /**
     * 解析并校验令牌，非法或过期令牌返回null
     */
    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT令牌已过期: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT令牌验签失败: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT令牌格式错误: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT令牌解析失败: {}", e.getMessage());
        }
        return null;
    }
}
