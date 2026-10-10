package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义用户详情服务
 * <p>
 * 认证时从用户关联的角色中构建权限串：
 * - 角色本身映射为 {@code ROLE_<角色名>}；
 * - 角色下的权限字符串（permissions）作为独立 GrantedAuthority 加入，
 *   供 hasAuthority / @PreAuthorize("hasAuthority('...')") 使用。
 * </p>
 *
 * @author kenyon
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AccountLoginGuard accountLoginGuard;

    public CustomUserDetailsService(UserRepository userRepository, AccountLoginGuard accountLoginGuard) {
        this.userRepository = userRepository;
        this.accountLoginGuard = accountLoginGuard;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // 账号是否被锁定单独传给 Spring（给出「账号已锁定」而不是「账号已禁用」），
        // 租户维度由守卫判定：被停用/被删除租户下的账号不能再拿到令牌
        boolean accountNonLocked = user.getStatus() != UserStatus.LOCKED;
        boolean enabled = user.getStatus() == UserStatus.ACTIVE
                && accountLoginGuard.isTenantUsable(user);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled,
                true,
                true,
                accountNonLocked,
                buildAuthorities(user)
        );
    }

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role == null || role.getName() == null) {
                    continue;
                }
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
                if (role.getPermissions() != null) {
                    for (String permission : role.getPermissions()) {
                        if (permission != null && !permission.isBlank()) {
                            authorities.add(new SimpleGrantedAuthority(permission));
                        }
                    }
                }
            }
        }
        return authorities;
    }
}
