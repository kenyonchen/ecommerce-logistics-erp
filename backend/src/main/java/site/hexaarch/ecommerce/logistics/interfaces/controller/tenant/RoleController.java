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
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;
import java.util.Set;

/**
 * 角色控制器，用于处理角色相关的HTTP请求。
 * <p>
 * 权限：{@code role:manage} 为租户级权限点，租户管理员可在<b>本租户</b>内建角色、配权限；
 * 平台管理员经 {@code ROLE_PLATFORM_ADMIN} 跨租户放行。系统角色（无租户归属）仅平台管理员可建可改，
 * 防止租户通过系统角色给自己或子账号提权。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage')")
@Tag(name = "角色管理", description = "角色相关的API接口")
public class RoleController {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    private final RoleServiceImpl roleService;

    @Autowired
    public RoleController(RoleServiceImpl roleService) {
        this.roleService = roleService;
    }

    /**
     * 创建角色
     */
    @Operation(summary = "创建角色", description = "创建新的角色；租户用户强制归属本租户，平台管理员必须显式指定租户")
    @PostMapping
    public ResponseEntity<Result<?>> createRole(@RequestBody CreateRoleRequest request) {
        request.setTenantId(resolveTenantScope(request.getTenantId()));
        Role role = roleService.createRole(
                request.getTenantId(),
                request.getName(),
                request.getDescription()
        );
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 创建系统角色
     */
    @Operation(summary = "创建系统角色", description = "创建新的系统角色（仅平台管理员）")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/system")
    public ResponseEntity<Result<?>> createSystemRole(@RequestBody CreateRoleRequest request) {
        Role role = roleService.createSystemRole(
                request.getName(),
                request.getDescription()
        );
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 获取角色列表
     */
    @Operation(summary = "获取角色列表", description = "根据租户ID获取角色列表；租户用户强制返回本租户角色")
    @GetMapping
    public ResponseEntity<Result<?>> getRoles(@Parameter(description = "租户ID，平台管理员必填") @RequestParam String tenantId) {
        List<Role> roles = roleService.getRolesByTenantId(resolveTenantScope(tenantId));
        return ResponseEntity.ok(Result.success(roles));
    }

    /**
     * 获取系统角色列表
     */
    @Operation(summary = "获取系统角色列表", description = "获取所有系统角色的列表（仅平台管理员）")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/system")
    public ResponseEntity<Result<?>> getSystemRoles() {
        List<Role> roles = roleService.getSystemRoles();
        return ResponseEntity.ok(Result.success(roles));
    }

    /**
     * 获取角色详情
     */
    @Operation(summary = "获取角色详情", description = "根据角色ID获取角色详情")
    @GetMapping("/{id}")
    public ResponseEntity<Result<?>> getRole(@Parameter(description = "角色ID") @PathVariable String id) {
        return ResponseEntity.ok(Result.success(requireAccessibleRole(id)));
    }

    /**
     * 更新角色
     */
    @Operation(summary = "更新角色", description = "更新角色信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<?>> updateRole(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody UpdateRoleRequest request) {
        requireAccessibleRole(id);
        Role role = roleService.updateRole(
                id,
                request.getName(),
                request.getDescription()
        );
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 删除角色
     */
    @Operation(summary = "删除角色", description = "删除角色")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<?>> deleteRole(@Parameter(description = "角色ID") @PathVariable String id) {
        requireAccessibleRole(id);
        roleService.deleteRole(id);
        return ResponseEntity.ok(Result.success("Role deleted successfully"));
    }

    /**
     * 为角色添加权限
     */
    @Operation(summary = "为角色添加权限", description = "为角色添加指定权限")
    @PostMapping("/{id}/permissions")
    public ResponseEntity<Result<?>> addPermission(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody AddPermissionRequest request) {
        requireAccessibleRole(id);
        Role role = roleService.addPermissionToRole(id, request.getPermission());
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 从角色移除权限
     */
    @Operation(summary = "从角色移除权限", description = "从角色中移除指定权限")
    @DeleteMapping("/{id}/permissions/{permission}")
    public ResponseEntity<Result<?>> removePermission(@Parameter(description = "角色ID") @PathVariable String id, @Parameter(description = "权限代码") @PathVariable String permission) {
        requireAccessibleRole(id);
        Role role = roleService.removePermissionFromRole(id, permission);
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 为角色设置权限集合
     */
    @Operation(summary = "为角色设置权限集合", description = "为角色设置权限集合")
    @PutMapping("/{id}/permissions")
    public ResponseEntity<Result<?>> setPermissions(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody SetPermissionsRequest request) {
        requireAccessibleRole(id);
        Role role = roleService.setPermissionsToRole(id, request.getPermissions());
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 解析租户范围（与 UserController / ShopController 同一约定）：
     * 平台管理员必须显式指定 tenantId；租户用户强制取登录租户，忽略入参。
     */
    private String resolveTenantScope(String requestedTenantId) {
        if (isPlatformAdmin()) {
            if (requestedTenantId == null || requestedTenantId.isBlank()) {
                throw new BusinessException("平台管理员操作角色数据必须显式指定 tenantId");
            }
            return requestedTenantId.trim();
        }
        if (!TenantContext.hasTenantId() || TenantContext.getTenantId().isBlank()) {
            throw new BusinessException("缺少租户上下文，无法执行角色管理操作");
        }
        return TenantContext.getTenantId();
    }

    /**
     * 按 id 操作前的归属校验：租户用户只能操作本租户的自建角色，
     * 系统角色（租户ID为空）仅平台管理员可访问，防止改权限点提权。
     */
    private Role requireAccessibleRole(String id) {
        Role role = roleService.getRoleById(id);
        if (!isPlatformAdmin()) {
            String currentTenantId = TenantContext.hasTenantId() ? TenantContext.getTenantId() : "";
            // 系统角色（tenantId=SYSTEM 哨兵值）不属于任何租户，仅平台管理员可访问
            if (currentTenantId.isBlank()
                    || RoleNames.SYSTEM_TENANT_ID.equals(role.getTenantId())
                    || !currentTenantId.equals(role.getTenantId())) {
                throw new BusinessException("无权访问其他租户的角色", "TENANT_ACCESS_DENIED");
            }
        }
        return role;
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
    public static class CreateRoleRequest {
        private String tenantId;
        private String name;
        private String description;

        // Getters and Setters
        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class UpdateRoleRequest {
        private String name;
        private String description;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class AddPermissionRequest {
        private String permission;

        // Getters and Setters
        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }
    }

    public static class SetPermissionsRequest {
        private List<String> permissions;

        // Getters and Setters
        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }
    }
}