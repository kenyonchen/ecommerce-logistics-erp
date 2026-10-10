package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.TenantStatus;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

/**
 * 账号可用性守卫：签发/续期令牌前必须过的两道状态检查。
 * <p>
 * 抽出来是因为有两条独立链路都要用它——认证（{@link CustomUserDetailsService}）与续期
 * （{@code /auth/refresh} 直接查库重签令牌，绕过了认证链路）。
 * </p>
 *
 * @author kenyon
 */
@Component
public class AccountLoginGuard {

    private final TenantRepository tenantRepository;

    public AccountLoginGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * 账号自身状态 + 所属租户状态都允许其拿到令牌。
     */
    public boolean isUsable(User user) {
        return user != null && user.getStatus() == UserStatus.ACTIVE && isTenantUsable(user);
    }

    /**
     * 所属租户必须存在且处于 ACTIVE。
     * <p>
     * {@code users.tenant_id} 没有指向 {@code tenants} 的外键：租户被停用甚至被删除后，
     * 其账号在数据库里原封不动地留着，只看账号状态等于放行一批"无主"身份。
     * </p>
     * <p>
     * 平台管理员例外——它就挂在自己管的租户上，一并拒绝等于把运营方锁在门外，
     * 现场再没人能恢复。
     * </p>
     */
    public boolean isTenantUsable(User user) {
        if (user == null) {
            return false;
        }
        if (isPlatformAdmin(user)) {
            return true;
        }
        String tenantId = user.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
    }

    private boolean isPlatformAdmin(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(RoleNames.PLATFORM_ADMIN::equals);
    }

    /**
     * 供错误文案使用：区分"账号被停"与"租户被停"，前者要去找平台管理员，后者是商务动作。
     */
    public String describeBlocker(User user) {
        if (user == null) {
            return "账号不存在";
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return "账号状态为 " + user.getStatus();
        }
        Tenant tenant = user.getTenantId() == null ? null
                : tenantRepository.findById(user.getTenantId()).orElse(null);
        if (tenant == null) {
            return "所属租户已不存在";
        }
        return "所属租户状态为 " + tenant.getStatus();
    }
}
