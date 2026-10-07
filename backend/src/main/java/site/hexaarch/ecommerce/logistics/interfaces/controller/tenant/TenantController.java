package site.hexaarch.ecommerce.logistics.interfaces.controller.tenant;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import site.hexaarch.ecommerce.logistics.application.service.tenant.TenantServiceImpl;
import site.hexaarch.ecommerce.logistics.application.service.tenant.UserServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 租户控制器，用于处理租户相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "租户管理", description = "租户相关的API接口")
public class TenantController {

    private final TenantServiceImpl tenantService;
    private final UserServiceImpl userService;

    @Autowired
    public TenantController(TenantServiceImpl tenantService, UserServiceImpl userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    /**
     * 创建租户（一步到位：同一事务内同时创建租户与管理员账号）
     */
    @Operation(summary = "创建租户", description = "创建新租户，并同时创建该租户的管理员账号（自动授予租户管理员角色）")
    @PostMapping
    public ResponseEntity<Result<?>> createTenant(@RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(
                request.getName(),
                request.getDescription(),
                request.getContactEmail(),
                request.getContactPhone(),
                request.getAdminUsername(),
                request.getAdminPassword(),
                request.getAdminEmail(),
                request.getAdminFirstName(),
                request.getAdminLastName()
        );
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 获取租户列表
     */
    @Operation(summary = "获取租户列表", description = "获取所有租户的列表")
    @GetMapping
    public ResponseEntity<Result<?>> getTenants() {
        List<Tenant> tenants = tenantService.getAllTenants();
        return ResponseEntity.ok(Result.success(tenants));
    }

    /**
     * 获取租户详情
     */
    @Operation(summary = "获取租户详情", description = "根据租户ID获取租户详情")
    @GetMapping("/{id}")
    public ResponseEntity<Result<?>> getTenant(@PathVariable String id) {
        Tenant tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 更新租户
     */
    @Operation(summary = "更新租户", description = "根据租户ID更新租户信息")
    @PutMapping("/{id}")
    public ResponseEntity<Result<?>> updateTenant(@PathVariable String id, @RequestBody UpdateTenantRequest request) {
        Tenant tenant = tenantService.updateTenant(
                id,
                request.getName(),
                request.getDescription(),
                request.getContactEmail(),
                request.getContactPhone()
        );
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 激活租户
     */
    @Operation(summary = "激活租户", description = "根据租户ID激活租户")
    @PutMapping("/{id}/activate")
    public ResponseEntity<Result<?>> activateTenant(@PathVariable String id) {
        Tenant tenant = tenantService.activateTenant(id);
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 暂停租户
     */
    @Operation(summary = "暂停租户", description = "根据租户ID暂停租户")
    @PutMapping("/{id}/suspend")
    public ResponseEntity<Result<?>> suspendTenant(@PathVariable String id) {
        Tenant tenant = tenantService.suspendTenant(id);
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 注销租户
     */
    @Operation(summary = "注销租户", description = "根据租户ID注销租户")        
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Result<?>> deactivateTenant(@PathVariable String id) {
        Tenant tenant = tenantService.deactivateTenant(id);
        return ResponseEntity.ok(Result.success(tenant));
    }

    /**
     * 删除租户
     */
    @Operation(summary = "删除租户", description = "根据租户ID删除租户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<?>> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.ok(Result.success("Tenant deleted successfully"));
    }

    /**
     * 为租户创建管理员
     * <p>
     * 走 {@link TenantServiceImpl#createTenantAdmin} 而非 {@code userService.createUser}，
     * 确保新账号直接带上 {@code TENANT_ADMIN} 角色与租户级权限点——
     * 否则建出来的账号 roles 为空，登录后处处 403。
     * </p>
     */
    @Operation(summary = "为租户创建管理员", description = "根据租户ID为租户创建管理员用户（自动授予租户管理员角色）")
    @PostMapping("/{id}/admin")
    public ResponseEntity<Result<?>> createTenantAdmin(@PathVariable String id, @RequestBody CreateUserRequest request) {
        User user = tenantService.createTenantAdmin(
                id,
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );
        return ResponseEntity.ok(Result.success(user));
    }

    // 请求类
    public static class CreateTenantRequest {
        private String name;
        private String description;
        private String contactEmail;
        private String contactPhone;
        // 租户管理员账号（创建租户时一并创建）
        private String adminUsername;
        private String adminPassword;
        private String adminEmail;
        private String adminFirstName;
        private String adminLastName;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public String getAdminUsername() { return adminUsername; }
        public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
        public String getAdminFirstName() { return adminFirstName; }
        public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
        public String getAdminLastName() { return adminLastName; }
        public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
    }

    public static class UpdateTenantRequest {
        private String name;
        private String description;
        private String contactEmail;
        private String contactPhone;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    }

    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String phone;
        private String firstName;
        private String lastName;

        // Getters and Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }
}