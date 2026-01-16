package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.RoleServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 角色控制器，用于处理角色相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "角色管理", description = "角色相关的API接口")
public class RoleController {

    private final RoleServiceImpl roleService;

    @Autowired
    public RoleController(RoleServiceImpl roleService) {
        this.roleService = roleService;
    }

    /**
     * 创建角色
     */
    @Operation(summary = "创建角色", description = "创建新的角色")
    @PostMapping
    public ResponseEntity<Result<?>> createRole(@RequestBody CreateRoleRequest request) {
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
    @Operation(summary = "创建系统角色", description = "创建新的系统角色")
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
    @Operation(summary = "获取角色列表", description = "根据租户ID获取角色列表")
    @GetMapping
    public ResponseEntity<Result<?>> getRoles(@Parameter(description = "租户ID") @RequestParam String tenantId) {
        List<Role> roles = roleService.getRolesByTenantId(tenantId);
        return ResponseEntity.ok(Result.success(roles));
    }

    /**
     * 获取系统角色列表
     */
    @Operation(summary = "获取系统角色列表", description = "获取所有系统角色的列表")
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
        Role role = roleService.getRoleById(id);
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 更新角色
     */
    @Operation(summary = "更新角色", description = "更新角色信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<?>> updateRole(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody UpdateRoleRequest request) {
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
        roleService.deleteRole(id);
        return ResponseEntity.ok(Result.success("Role deleted successfully"));
    }

    /**
     * 为角色添加权限
     */
    @Operation(summary = "为角色添加权限", description = "为角色添加指定权限")
    @PostMapping("/{id}/permissions")
    public ResponseEntity<Result<?>> addPermission(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody AddPermissionRequest request) {
        Role role = roleService.addPermissionToRole(id, request.getPermission());
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 从角色移除权限
     */
    @Operation(summary = "从角色移除权限", description = "从角色中移除指定权限")
    @DeleteMapping("/{id}/permissions/{permission}")
    public ResponseEntity<Result<?>> removePermission(@Parameter(description = "角色ID") @PathVariable String id, @Parameter(description = "权限代码") @PathVariable String permission) {
        Role role = roleService.removePermissionFromRole(id, permission);
        return ResponseEntity.ok(Result.success(role));
    }

    /**
     * 为角色设置权限集合
     */
    @Operation(summary = "为角色设置权限集合", description = "为角色设置权限集合")
    @PutMapping("/{id}/permissions")
    public ResponseEntity<Result<?>> setPermissions(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody SetPermissionsRequest request) {
        Role role = roleService.setPermissionsToRole(id, request.getPermissions());
        return ResponseEntity.ok(Result.success(role));
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