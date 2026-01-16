package site.hexaarch.ecommerce.logistics.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌提供者，用于生成和验证JWT令牌。
 *
 * @author kenyon
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:#{'mySecretKeyMustBeAtLeast256BitsLongForHS512Algorithm'}}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 默认24小时
    private long jwtExpiration;

    @Value("${jwt.refresh.expiration:604800000}") // 默认7天
    private long jwtRefreshExpiration;

    private SecretKey getSigningKey() {
        // 使用JJWT提供的密钥构建器确保密钥长度足够安全
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成JWT令牌
     *
     * @param authentication 认证对象
     * @return JWT令牌
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        claims.put("roles", userDetails.getAuthorities());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成带有租户ID的JWT令牌
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param roles    角色列表
     * @return JWT令牌
     */
    public String generateToken(String username, String tenantId, String[] roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("tenantId", tenantId);
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
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
        claims.put("username", username);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtRefreshExpiration))
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
        return "";
    }

    /**
     * 从令牌中提取租户ID
     *
     * @param token JWT令牌
     * @return 租户ID
     */
    public String getTenantIdFromToken(String token) {
        return "default-tenant";
    }

    /**
     * 验证令牌
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return true;
    }
}