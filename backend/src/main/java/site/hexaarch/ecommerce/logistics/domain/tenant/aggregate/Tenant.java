package site.hexaarch.ecommerce.logistics.domain.tenant.aggregate;

import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.TenantStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 租户聚合根
 *
 * @author kenyon
 */
public class Tenant extends BaseAggregateRoot<Tenant> {
    private String id;
    private String name;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private TenantStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 租户关联的用户
    private final Set<User> users = new HashSet<>();

    // 租户关联的角色
    private final Set<Role> roles = new HashSet<>();

    // 私有构造函数，通过工厂方法创建
    private Tenant() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = TenantStatus.ACTIVE;
    }

    // 工厂方法
    public static Tenant create(String name, String description, String contactEmail, String contactPhone) {
        Tenant tenant = new Tenant();
        tenant.id = UUID.randomUUID().toString();  // 生成唯一ID
        tenant.name = name;
        tenant.description = description;
        tenant.contactEmail = contactEmail;
        tenant.contactPhone = contactPhone;
        return tenant;
    }

    // 激活租户
    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 暂停租户
    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    // 注销租户
    public void deactivate() {
        this.status = TenantStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 更新租户信息
    public void updateInfo(String name, String description, String contactEmail, String contactPhone) {
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.updatedAt = LocalDateTime.now();
    }

    // 添加用户
    public void addUser(User user) {
        this.users.add(user);
    }

    // 移除用户
    public void removeUser(User user) {
        this.users.remove(user);
    }

    // 添加角色
    public void addRole(Role role) {
        this.roles.add(role);
    }

    // 移除角色
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<User> getUsers() {
        return users;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}