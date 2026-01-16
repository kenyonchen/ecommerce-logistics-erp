package site.hexaarch.ecommerce.logistics.domain.tenant.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import site.hexaarch.ecommerce.logistics.domain.common.BaseEntity;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户实体
 *
 * @author kenyon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
    private String id;
    private String tenantId;
    private String username;
    private String password;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 用户关联的角色
    private final Set<Role> roles = new HashSet<>();

    // 私有构造函数，通过工厂方法创建
    private User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
    }

    // 工厂方法
    public static User create(String tenantId, String username, String password, String email,
                              String phone, String firstName, String lastName) {
        User user = new User();
        user.tenantId = tenantId;
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.firstName = firstName;
        user.lastName = lastName;
        return user;
    }

    // 激活用户
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 禁用用户
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 锁定用户
    public void lock() {
        this.status = UserStatus.LOCKED;
        this.updatedAt = LocalDateTime.now();
    }

    // 更新用户信息
    public void updateInfo(String email, String phone, String firstName, String lastName) {
        this.email = email;
        this.phone = phone;
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = LocalDateTime.now();
    }

    // 更新密码
    public void updatePassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    // 添加角色
    public void addRole(Role role) {
        this.roles.add(role);
    }

    // 移除角色
    public void removeRole(Role role) {
        this.roles.remove(role);
    }
}