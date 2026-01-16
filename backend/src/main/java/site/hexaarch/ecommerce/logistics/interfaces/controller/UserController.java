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
import site.hexaarch.ecommerce.logistics.application.service.UserServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 用户控制器，用于处理用户相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户相关的API接口")
public class UserController {

    private final UserServiceImpl userService;

    @Autowired
    public UserController(UserServiceImpl userService) {
        this.userService = userService;
    }

    /**
     * 创建用户
     */
    @Operation(summary = "创建用户", description = "创建新的用户")
    @PostMapping
    public ResponseEntity<Result<?>> createUser(@RequestBody CreateUserRequest request) {
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
    @Operation(summary = "获取用户列表", description = "根据租户ID获取用户列表")
    @GetMapping
    public ResponseEntity<Result<?>> getUsers(@Parameter(description = "租户ID") @RequestParam String tenantId) {
        List<User> users = userService.getUsersByTenantId(tenantId);
        return ResponseEntity.ok(Result.success(users));
    }

    /**
     * 获取用户详情
     */
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详情")
    @GetMapping("/{id}")
    public ResponseEntity<Result<?>> getUser(@Parameter(description = "用户ID") @PathVariable String id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<?>> updateUser(@Parameter(description = "用户ID") @PathVariable String id, @RequestBody UpdateUserRequest request) {
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
        User user = userService.updatePassword(id, request.getPassword());
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 激活用户
     */
    @Operation(summary = "激活用户", description = "激活用户账号")
    @PutMapping("/{id}/activate")
    public ResponseEntity<Result<?>> activateUser(@Parameter(description = "用户ID") @PathVariable String id) {
        User user = userService.activateUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 禁用用户
     */
    @Operation(summary = "禁用用户", description = "禁用用户账号")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Result<?>> deactivateUser(@Parameter(description = "用户ID") @PathVariable String id) {
        User user = userService.deactivateUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 锁定用户
     */
    @Operation(summary = "锁定用户", description = "锁定用户账号")
    @PutMapping("/{id}/lock")
    public ResponseEntity<Result<?>> lockUser(@Parameter(description = "用户ID") @PathVariable String id) {
        User user = userService.lockUser(id);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除用户账号")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<?>> deleteUser(@Parameter(description = "用户ID") @PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("User deleted successfully"));
    }

    /**
     * 为用户分配角色
     */
    @Operation(summary = "为用户分配角色", description = "为用户分配指定角色")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Result<?>> assignRole(@Parameter(description = "用户ID") @PathVariable String id, @RequestBody AssignRoleRequest request) {
        User user = userService.assignRoleToUser(id, request.getRoleId());
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 从用户移除角色
     */
    @Operation(summary = "从用户移除角色", description = "从用户账号中移除指定角色")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<Result<?>> removeRole(@Parameter(description = "用户ID") @PathVariable String id, @Parameter(description = "角色ID") @PathVariable String roleId) {
        User user = userService.removeRoleFromUser(id, roleId);
        return ResponseEntity.ok(Result.success(user));
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