package site.hexaarch.ecommerce.logistics.interfaces.controller.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.tenant.RoleServiceImpl;
import site.hexaarch.ecommerce.logistics.application.service.tenant.UserServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;
import java.util.Set;

/**
 * 用户控制器，用于处理用户相关的HTTP请求。
 * <p>
 * 权限：{@code user:manage} 为租户级权限点，租户管理员持有后可管理<b>本租户</b>的子账号；
 * 平台管理员经 {@code ROLE_PLATFORM_ADMIN} 跨租户放行（列表/创建必须显式指定 tenantId）。
 * 租户用户的按 id 操作统一做归属校验，且不能操作平台管理员账号（防止租户侧提权/删号）。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage')")
@Tag(name = "用户管理", description = "用户相关的API接口")
public class UserController {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    private final UserServiceImpl userService;
    private final RoleServiceImpl roleService;

    @Autowired
    public UserController(UserServiceImpl userService, RoleServiceImpl roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    /**
     * 创建用户
     */
    @Operation(summary = "创建用户", description = "创建新的用户；租户用户强制归属本租户，平台管理员必须显式指定租户")
    @PostMapping
    public ResponseEntity<Result<?>> createUser(@RequestBody CreateUserRequest request) {
        request.setTenantId(resolveTenantScope(request.getTenantId()));
        User user = userService.createUser(
                request.getTenantId(),
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getPhone(),
                request.getFirstName(),
                request.getLastName()
        );
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 获取用户列表
     */
    @Operation(summary = "获取用户列表", description = "根据租户ID获取用户列表；租户用户强制返回本租户用户")
    @GetMapping
    public ResponseEntity<Result<?>> getUsers(@Parameter(description = "租户ID，平台管理员必填") @RequestParam String tenantId) {
        List<User> users = userService.getUsersByTenantId(resolveTenantScope(tenantId));
        return ResponseEntity.ok(Result.success(users));
    }

    /**
     * 获取用户详情
     */
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详情")
    @GetMapping("/{id}")
    public ResponseEntity<Result<?>> getUser(@Parameter(description = "用户ID") @PathVariable String id) {
        return ResponseEntity.ok(Result.success(requireAccessibleUser(id)));
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<?>> updateUser(@Parameter(description = "用户ID") @PathVariable String id, @RequestBody UpdateUserRequest request) {
        requireAccessibleUser(id);
        User user = userService.updateUser(
                id,
                request.getEmail(),
                request.getPhone(),
                request.getFirstName(),
                request.getLastName()
        );
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 更新用户密码
     */
    @Operation(summary = "更新用户密码", description = "更新用户密码")
    @PutMapping("/{id}/password")
    public ResponseEntity<Result<?>> updatePassword(@Parameter(description = "用户ID") @PathVariable String id, @RequestBody UpdatePasswordRequest request) {
        requireAccessibleUser(id);
        User user = userService.updatePassword(id, request.getPassword());
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 激活用户
     */
    @Operation(summary = "激活用户", description = "激活用户账号")
    @PutMapping("/{id}/activate")
    public ResponseEntity<Result<?>> activateUser(@Parameter(description = "用户ID") @PathVariable String id) {
        requireAccessibleUser(id);
        User user = userService.activateUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 禁用用户
     */
    @Operation(summary = "禁用用户", description = "禁用用户账号")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Result<?>> deactivateUser(@Parameter(description = "用户ID") @PathVariable String id) {
        requireAccessibleUser(id);
        User user = userService.deactivateUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 锁定用户
     */
    @Operation(summary = "锁定用户", description = "锁定用户账号")
    @PutMapping("/{id}/lock")
    public ResponseEntity<Result<?>> lockUser(@Parameter(description = "用户ID") @PathVariable String id) {
        requireAccessibleUser(id);
        User user = userService.lockUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除用户账号")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<?>> deleteUser(@Parameter(description = "用户ID") @PathVariable String id) {
        requireAccessibleUser(id);
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("User deleted successfully"));
    }

    /**
     * 为用户分配角色
     */
    @Operation(summary = "为用户分配角色", description = "为用户分配指定角色；租户用户只能分配本租户的角色，系统角色仅平台管理员可分配")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Result<?>> assignRole(@Parameter(description = "用户ID") @PathVariable String id, @RequestBody AssignRoleRequest request) {
        User target = requireAccessibleUser(id);
        ensureRoleAssignable(target, request.getRoleId());
        User user = userService.assignRoleToUser(id, request.getRoleId());
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 从用户移除角色
     */
    @Operation(summary = "从用户移除角色", description = "从用户账号中移除指定角色；租户用户只能操作本租户的角色")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<Result<?>> removeRole(@Parameter(description = "用户ID") @PathVariable String id, @Parameter(description = "角色ID") @PathVariable String roleId) {
        User target = requireAccessibleUser(id);
        ensureRoleAssignable(target, roleId);
        User user = userService.removeRoleFromUser(id, roleId);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 解析租户范围（与 ShopController 同一约定）：
     * 平台管理员必须显式指定 tenantId；租户用户强制取登录租户，忽略入参，杜绝横向越权。
     */
    private String resolveTenantScope(String requestedTenantId) {
        if (isPlatformAdmin()) {
            if (requestedTenantId == null || requestedTenantId.isBlank()) {
                throw new BusinessException("平台管理员操作用户数据必须显式指定 tenantId");
            }
            return requestedTenantId.trim();
        }
        if (!TenantContext.hasTenantId() || TenantContext.getTenantId().isBlank()) {
            throw new BusinessException("缺少租户上下文，无法执行用户管理操作");
        }
        return TenantContext.getTenantId();
    }

    /**
     * 按 id 操作前的归属校验：租户用户只能操作本租户用户，
     * 且不能操作持有平台管理员角色的账号（否则可锁号/改密/删号提权）。
     */
    private User requireAccessibleUser(String id) {
        User target = userService.getUserById(id);
        if (!isPlatformAdmin()) {
            String currentTenantId = TenantContext.hasTenantId() ? TenantContext.getTenantId() : "";
            if (currentTenantId.isBlank() || !currentTenantId.equals(target.getTenantId())) {
                throw new BusinessException("无权访问其他租户的用户", "TENANT_ACCESS_DENIED");
            }
            boolean platformAdminUser = target.getRoles() != null && target.getRoles().stream()
                    .anyMatch(role -> RoleNames.PLATFORM_ADMIN.equals(role.getName()));
            if (platformAdminUser) {
                throw new BusinessException("租户账号无权操作平台管理员账号");
            }
        }
        return target;
    }

    /**
     * 角色分配校验：租户用户只能分配/移除本租户的角色，
     * 系统角色（无租户归属，含平台管理员）仅平台管理员可操作，否则等于租户侧直接提权。
     */
    private void ensureRoleAssignable(User target, String roleId) {
        if (isPlatformAdmin()) {
            return;
        }
        String currentTenantId = TenantContext.hasTenantId() ? TenantContext.getTenantId() : "";
        site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role role = roleService.getRoleById(roleId);
        // 系统角色（tenantId=SYSTEM 哨兵值）不属于任何租户，仅平台管理员可分配
        if (role == null || RoleNames.SYSTEM_TENANT_ID.equals(role.getTenantId())
                || !currentTenantId.equals(role.getTenantId())) {
            throw new BusinessException("无权操作该角色", "TENANT_ACCESS_DENIED");
        }
    }

    private boolean isPlatformAdmin() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = org.springframework.security.core.authority.AuthorityUtils
                .authorityListToSet(authentication.getAuthorities());
        return authorities.contains(ROLE_PLATFORM_ADMIN);
    }

    // 请求类
    public static class CreateUserRequest {
        private String tenantId;
        private String username;
        private String password;
        private String email;
        private String phone;
        private String firstName;
        private String lastName;

        // Getters and Setters
        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    public static class UpdateUserRequest {
        private String email;
        private String phone;
        private String firstName;
        private String lastName;

        // Getters and Setters
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    public static class UpdatePasswordRequest {
        private String password;

        // Getters and Setters
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AssignRoleRequest {
        private String roleId;

        // Getters and Setters
        public String getRoleId() {
            return roleId;
        }

        public void setRoleId(String roleId) {
            this.roleId = roleId;
        }
    }
}